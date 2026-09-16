package com.MapleLeaf.MiaoWang.rag;

import com.MapleLeaf.MiaoWang.dto.req.RagAskReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.RagAskRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.RagSourceRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * RAG 问答服务 —— 把"检索"和"生成"串起来的地方，整个 RAG 的主流程就在这个文件里。
 *
 * <h3>一、RAG 就两步</h3>
 * <pre>
 *   第 1 步 检索（Retrieval）：拿用户问题去知识库找相关资料   → 谁做？KnowledgeRetriever
 *   第 2 步 生成（Generation）：把资料 + 问题一起交给大模型回答 → 谁做？本类
 * </pre>
 * 不做 RAG，模型只能凭记忆瞎答；做了 RAG，它答的是你自己知识库里的内容。
 *
 * <h3>二、实际发给模型的内容长什么样（buildContext 拼出来的）</h3>
 * <pre>
 *   SystemMessage（人设 + 规矩，见下面的 SYSTEM_PROMPT）：
 *       你是一位专业的宠物养护助手。请严格依据下方提供的知识碎片回答问题：……
 *
 *   UserMessage（检索结果 + 用户问题）：
 *       知识碎片：
 *       [1] 睡觉时打呼噜：猫咪睡觉时发出呼噜声属于正常现象……
 *       [2] 呼噜呼噜（睡觉打鼾）：……
 *
 *       问题：小猫一直发出呼噜声是为什么
 * </pre>
 * 那个 [1] [2] 编号就是"引用来源"的来源：模型被要求按编号标注，前端再渲染成资料卡片。
 *
 * <h3>三、检索不到资料时会怎样</h3>
 * 不硬答。userText 会换成"知识库中未检索到相关内容，请直接根据自己的知识回答，并说明这是通用建议"，
 * 配合 SYSTEM_PROMPT 第 3 条，模型会明确回答"知识库中没有找到相关内容"，而不是编造 —— 这是防幻觉的关键。
 */
@Slf4j
@Service
public class RagAskService {

    /**
     * 系统提示词 = 给模型定的"人设 + 规矩"。RAG 效果一半靠检索质量，一半靠这段话。
     * 第 3 条是防幻觉的核心：资料不够就明说，不许编。
     * 第 4 条是领域安全兜底：涉及疾病要提醒就医。
     */
    private static final String SYSTEM_PROMPT = """
            你是一位专业的宠物养护助手。请严格依据下方提供的知识碎片回答问题：
            1. 回答要简洁、准确、贴近养宠实际，优先使用碎片内容作答
            2. 引用碎片处标注来源编号，如 [1]、[2]
            3. 若碎片不足以回答，明确说明"知识库中没有找到相关内容"，不要编造
            4. 涉及疾病或严重症状时，提醒及时咨询专业兽医
            """;

    /** 检索器：负责"找资料"（向量路 + 关键词路，细节在 KnowledgeRetriever） */
    private final KnowledgeRetriever knowledgeRetriever;

    /** 对话模型：负责"看着资料写答案"，由 DashScopeConfig 提供 */
    private final ChatModel chatModel;

    /**
     * @Qualifier("dashscopeChatModel") 指明到底注入哪个 ChatModel ——
     * 容器里可能还有别的模型 Bean，不加 Qualifier 会报"找到多个候选 Bean"。
     */
    public RagAskService(KnowledgeRetriever knowledgeRetriever,
                         @Qualifier("dashscopeChatModel") ChatModel chatModel) {
        this.knowledgeRetriever = knowledgeRetriever;
        this.chatModel = chatModel;
    }

    /**
     * 一次问答的中间结果：检索阶段产出，生成阶段消费。
     *
     * @param userText     拼好的用户消息（"知识碎片：[1]… [2]…" + "问题：…"），直接送给模型
     * @param sources      给前端展示的来源卡片列表（只有 id/标题/物种/分类）
     * @param hasKnowledge 是否检索到了资料（检索为空时为 false）
     */
    public record AskContext(String userText, List<RagSourceRespDTO> sources, boolean hasKnowledge) {
    }

    /**
     * ① 混合检索 + 拼上下文（流式/非流式共用）。
     * 检索是同步、很快的，所以 SSE 流可以先把 sources 推给前端，再慢慢等模型生成。
     */
    public AskContext buildContext(RagAskReqDTO request) {
        String question = request.getQuestion();
        log.info("RAG 提问: {}", question);

        // 1) 混合检索：向量（语义）+ 关键词（字面）双路召回，再用 RRF 融合排序，取前 5 条
        List<KnowledgeRetriever.RetrievedFragment> fragments =
                knowledgeRetriever.retrieve(question, request.getSpecies());

        // 2) 把检索结果拼成带编号的文本："[1] 标题：正文"
        //    编号是为了让模型能写 [1] 这种引用，前端据此展示来源
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < fragments.size(); i++) {
            KnowledgeRetriever.RetrievedFragment f = fragments.get(i);
            context.append(String.format("[%d] %s：%s%n", i + 1,
                    nullToEmpty(f.title()), nullToEmpty(f.content())));
        }
        // 3) 检索不到资料时给一句明确的兜底指令，避免模型凭空编造
        String userText = (context.isEmpty()
                ? "知识库中未检索到相关内容，请直接根据自己的知识回答，并说明这是通用建议。\n"
                : "知识碎片：\n" + context + "\n")
                + "问题：" + question;

        // 4) 单独组装给前端用的来源卡片（只带 id/标题/物种/分类，不带正文，省流量）
        List<RagSourceRespDTO> sources = fragments.stream()
                .map(f -> RagSourceRespDTO.builder()
                        .id(f.id())
                        .title(f.title())
                        .species(f.species())
                        .categoryCode(f.categoryCode())
                        .build())
                .toList();
        return new AskContext(userText, sources, !fragments.isEmpty());
    }

    /**
     * ② 非流式：调一次模型，等它把整段答案写完再一次性返回。
     * chatModel.call() 是阻塞调用，适合后台任务或内部服务调用。
     */
    public String answer(AskContext context) {
        return chatModel.call(buildPrompt(context)).getResult().getOutput().getText();
    }

    /**
     * ② 流式：调一次模型，拿到 Flux（数据流），模型每吐一点就往下游发一点。
     * chatModel.stream() 返回 Flux&lt;ChatResponse&gt;，由 RagController 转成 SSE 推给浏览器。
     * 注意：这个流里有很多帧的文字是空的（模型在"思考"），控制器会把空帧过滤掉。
     */
    public Flux<ChatResponse> streamAnswer(AskContext context) {
        return chatModel.stream(buildPrompt(context));
    }

    /**
     * 兼容入口：检索 + 非流式生成，返回完整应答
     */
    public RagAskRespDTO ask(RagAskReqDTO request) {
        AskContext context = buildContext(request);
        RagAskRespDTO resp = new RagAskRespDTO();
        resp.setAnswer(answer(context));
        resp.setSources(context.sources());
        resp.setHasKnowledge(context.hasKnowledge());
        return resp;
    }

    /**
     * 组装最终发给模型的两条消息：一条 System（人设 + 规矩）、一条 User（资料 + 问题）。
     * 这就是所有大模型通用的调用格式 —— messages 数组。
     */
    private Prompt buildPrompt(AskContext context) {
        return new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(context.userText())));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}