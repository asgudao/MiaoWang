package com.MapleLeaf.MiaoWang.rag;

import com.MapleLeaf.MiaoWang.dao.entity.AiMessageDO;
import com.MapleLeaf.MiaoWang.dto.req.RagAskReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.RagSourceRespDTO;
import com.MapleLeaf.MiaoWang.support.TokenUsageExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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
            5. 这是连续对话：用户可能用"它/这个/上面说的"指代前面聊过的宠物或话题，请结合上下文理解
            """;

    /** 短问题阈值：不超过这么多字就认为它可能依赖上文（用于检索 query 改写） */
    private static final int SHORT_QUESTION = 8;

    /** 代词/指代词：命中就把上一轮提问拼进检索 query */
    private static final Pattern PRONOUN = Pattern.compile("它|他|她|这个|那个|这种|那样|上面|刚才|继续");

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
     * @param history      历史对话（已转成大模型 messages 格式，时间正序；首轮为空列表）
     */
    public record AskContext(String userText, List<RagSourceRespDTO> sources, boolean hasKnowledge,
                             List<Message> history) {
    }

    /**
     * 一次非流式问答的结果：Controller 用它组装返回体，同时把 usage 交给调用日志做成本统计。
     *
     * @param usage 本次 token 用量；<b>可能为 null</b>（模型没返回用量时），
     *              此时成本统计会跳过而不是算成 0 —— 宁可缺数据也不编数字
     * @param reasoning 模型的思考过程（思考模型才有，可能为 null）。<b>只用于展示，不落库</b>：
     *                  落库后会被当成历史消息再喂回模型，污染上下文（Qwen 官方也不建议回传 reasoning）
     */
    public record AskResult(String answer, List<RagSourceRespDTO> sources, boolean hasKnowledge, Usage usage,
                            String reasoning) {
    }

    /**
     * ① 混合检索 + 拼上下文（流式/非流式共用）。不带历史 = 单轮问答。
     * 检索是同步、很快的，所以 SSE 流可以先把 sources 推给前端，再慢慢等模型生成。
     */
    public AskContext buildContext(RagAskReqDTO request) {
        return buildContext(request, List.of());
    }

    /**
     * ① 带历史的多轮版本。
     *
     * <p>这里有件事是分开的：<b>检索用改写后的 query</b>（代词补全，为了召回率），
     * <b>提示词里仍旧用用户原话</b> —— 改写只是检索的辅助手段，不该篡改模型看到的原始问题。
     */
    public AskContext buildContext(RagAskReqDTO request, List<AiMessageDO> history) {
        String question = request.getQuestion();
        String searchQuery = rewriteQuery(question, history);
        log.info("RAG 提问: {}（检索 query: {}，带入历史 {} 条）", question, searchQuery,
                history == null ? 0 : history.size());

        // 1) 混合检索：向量（语义）+ 关键词（字面）双路召回，再用 RRF 融合排序，取前 5 条
        List<KnowledgeRetriever.RetrievedFragment> fragments =
                knowledgeRetriever.retrieve(searchQuery, request.getSpecies());

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
        return new AskContext(userText, sources, !fragments.isEmpty(), toMessages(history));
    }

    /**
     * ② 非流式：调一次模型，等它把整段答案写完再一次性返回。
     * chatModel.call() 是阻塞调用，适合后台任务或内部服务调用。
     * <p>
     * 这里返回原始的 {@link ChatResponse} 而不是 String：除了答案文本，
     * 还要取 token 用量交给调用日志做成本统计。
     */
    public ChatResponse answer(AskContext context) {
        return chatModel.call(buildPrompt(context));
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
     * 兼容入口：检索 + 非流式生成（单轮）
     */
    public AskResult ask(RagAskReqDTO request) {
        return ask(request, List.of());
    }

    /**
     * 兼容入口：检索 + 非流式生成，返回完整应答 + 本次 token 用量。
     * 流式与非流式共用 TokenUsageExtractor 的判空口径（思考模型的流里很多帧没有 text）。
     *
     * @param history 多轮上下文（最近若干条历史消息，可为空列表）
     */
    public AskResult ask(RagAskReqDTO request, List<AiMessageDO> history) {
        // ① 检索 + 拼上下文（带历史）
        AskContext context = buildContext(request, history);
        // ② 生成（阻塞调用）
        ChatResponse response = answer(context);
        return new AskResult(TokenUsageExtractor.textOf(response), context.sources(),
                context.hasKnowledge(), TokenUsageExtractor.usageOf(response),
                TokenUsageExtractor.reasoningOf(response));
    }

    /**
     * 组装最终发给模型的 messages：一条 System（人设 + 规矩）+ 若干历史 + 一条 User（资料 + 问题）。
     * 这就是所有大模型通用的调用格式 —— messages 数组。
     * 多轮对话不是把历史拼成一个大字符串，而是还原成"用户/AI 交替"的数组，
     * 模型才能分清哪句是谁说的。
     */
    private Prompt buildPrompt(AskContext context) {
        List<Message> messages = new ArrayList<>(context.history().size() + 2);
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.addAll(context.history());
        messages.add(new UserMessage(context.userText()));
        return new Prompt(messages);
    }

    /**
     * 检索用的 query 改写（便宜版）：问题很短、或含"它/这个/继续"这类指代词时，
     * 把上一轮的提问拼在前面 ——
     * <pre>
     *   上一轮：猫一直打喷嚏怎么办
     *   本轮：  它是不是感冒了        ← 单看这句，检索会跑偏
     *   改写后：猫一直打喷嚏怎么办 它是不是感冒了   ← 向量/关键词两路都能召回"打喷嚏"
     * </pre>
     * 代价是 0（不额外调模型）。更准的做法是让小模型把问题改写成独立完整的问句，
     * 但那要多一次调用（多花 token、多 200~500ms 延迟），当前阶段不值得。
     */
    private static String rewriteQuery(String question, List<AiMessageDO> history) {
        if (question == null || history == null || history.isEmpty()) {
            return question;
        }
        boolean needsContext = question.length() <= SHORT_QUESTION || PRONOUN.matcher(question).find();
        if (!needsContext) {
            return question;
        }
        // 找最近一条用户提问当上下文（历史是时间正序，所以从末尾往前找）
        for (int i = history.size() - 1; i >= 0; i--) {
            AiMessageDO row = history.get(i);
            if (row.getRole() != null && row.getRole() == 1
                    && row.getContent() != null && !row.getContent().isBlank()) {
                return row.getContent() + " " + question;
            }
        }
        return question;
    }

    /**
     * 库里的历史消息 → 大模型的 messages 格式。role=1 是用户，其余（2=AI）当回答。
     */
    private static List<Message> toMessages(List<AiMessageDO> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Message> messages = new ArrayList<>(history.size());
        for (AiMessageDO row : history) {
            if (row.getContent() == null || row.getContent().isBlank()) {
                continue;
            }
            messages.add(row.getRole() != null && row.getRole() == 1
                    ? new UserMessage(row.getContent())
                    : new AssistantMessage(row.getContent()));
        }
        return messages;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}