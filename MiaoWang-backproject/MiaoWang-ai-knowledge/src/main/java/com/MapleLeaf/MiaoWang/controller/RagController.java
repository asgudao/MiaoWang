package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.exception.AbstractException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.config.LlmProperties;
import com.MapleLeaf.MiaoWang.dao.entity.AiMessageDO;
import com.MapleLeaf.MiaoWang.dto.req.RagAnalyzeReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.RagAskReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.RagAskRespDTO;
import com.MapleLeaf.MiaoWang.enums.AiErrorCodeEnum;
import com.MapleLeaf.MiaoWang.rag.RagAskService;
import com.MapleLeaf.MiaoWang.rag.RagIndexService;
import com.MapleLeaf.MiaoWang.service.AiConversationService;
import com.MapleLeaf.MiaoWang.support.AiCallLogRecorder;
import com.MapleLeaf.MiaoWang.support.CurrentUid;
import com.MapleLeaf.MiaoWang.support.RagQuotaGuard;
import com.MapleLeaf.MiaoWang.support.TokenUsageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RAG 相关接口：
 * <ul>
 *   <li>/ai/rag/ask          宠物健康问答（非流式，一次性返回 answer + sources）</li>
 *   <li>/ai/rag/ask/stream   宠物健康问答（SSE 流式：meta → sources → message… → done）</li>
 *   <li>/ai/rag/index/build  重建向量索引（把知识库已发布碎片灌入 Milvus）</li>
 *   <li>/ai/rag/internal/analyze  服务间 AI 健康解读（不扣额度、仅服务间调用）</li>
 * </ul>
 *
 * <p><b>额度</b>：ask 与 ask/stream 都要登录，并在调用大模型之前先扣一次 RAG 额度
 * （免费档 5 次/月，会员档按天给，`-1` 表示不限次数）。次数用完返回 `B000702`；
 * 生成失败会把刚扣的那次回退掉，"失败不扣次数"。index/build 是运维动作，不扣额度。
 *
 * <p><b>多轮上下文</b>：请求带上 conversationId 就在同一个会话里继续追问，不带就自动新建会话；
 * 该会话最近 6 轮（2000 字以内）的历史会一起发给模型。所有问答都会落库成消息。
 *
 * <p><b>调用日志</b>：四条链路都会记一条 {@link AiCallLogRecorder} 日志（异步，不占请求线程）。
 * 失败也要记 —— 失败的调用在消息表里没有对应行，若只记成功，失败率与成本会被系统性低估。
 */
@Slf4j
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagAskService ragAskService;
    private final RagIndexService ragIndexService;
    private final RagQuotaGuard ragQuotaGuard;
    private final AiCallLogRecorder aiCallLogRecorder;
    private final AiConversationService aiConversationService;
    private final CurrentUid currentUid;
    private final LlmProperties llmProperties;

    /**
     * 非流式问答。额度顺序：先扣后调 —— 扣不到额度直接拒绝，
     * 避免"先回答后扣次数"（答案已经产生成本，扣不到就白送）。
     */
    @PostMapping("/ask")
    public Result<RagAskRespDTO> ask(@RequestBody RagAskReqDTO requestParam) {
        validate(requestParam);
        Long uid = currentUid.require();
        boolean consumed = ragQuotaGuard.consumeOrThrow(uid);
        long start = System.currentTimeMillis();
        Long conversationId = null;
        try {
            // ① 会话：不带 conversationId 就自动新建一个（标题取提问前 20 字）
            conversationId = aiConversationService.resolveConversation(uid, requestParam.getConversationId(),
                    requestParam.getQuestion());
            // ② 取历史必须放在"写入本轮提问"之前，否则本轮问题会在提示词里出现两次
            List<AiMessageDO> history = aiConversationService.historyForPrompt(conversationId);
            // ③ 先落用户提问：模型调用失败时这条提问仍留在历史里（和 ChatGPT 的行为一致）
            aiConversationService.appendUserMessage(uid, conversationId, requestParam.getQuestion());

            // ④ 检索 + 生成（带历史 = 多轮）
            RagAskService.AskResult result = ragAskService.ask(requestParam, history);
            long latency = System.currentTimeMillis() - start;

            // ⑤ 落 AI 回答，拿到 messageId 给前端的 👍/👎 用
            Long messageId = aiConversationService.appendAssistantMessage(uid, conversationId, result.answer(),
                    result.sources(), result.hasKnowledge(), result.usage(), latency);
            aiCallLogRecorder.recordSuccess(AiCallLogRecorder.SCENE_ASK, uid, result.usage(), latency,
                    conversationId, messageId);

            RagAskRespDTO resp = new RagAskRespDTO();
            resp.setAnswer(result.answer());
            resp.setSources(result.sources());
            resp.setHasKnowledge(result.hasKnowledge());
            resp.setConversationId(conversationId);
            resp.setMessageId(messageId);
            // 思考过程只在开关打开时返回（见 showThinking()）
            resp.setReasoning(showThinking() ? result.reasoning() : null);
            return Results.success(resp);
        } catch (RuntimeException e) {
            // 生成失败不扣次数：只有真扣过（非不限次数档位）才需要回退
            if (consumed) {
                ragQuotaGuard.refund(uid);
            }
            aiCallLogRecorder.recordFailure(AiCallLogRecorder.SCENE_ASK, uid, errorCodeOf(e), e.getMessage(),
                    System.currentTimeMillis() - start, conversationId);
            throw e;
        }
    }

    /**
     * 服务间：AI 健康解读（healthy 模块调用）
     *
     * <p>与 {@code /ask} 的区别：
     * <ul>
     *   <li><b>不校验登录、不扣 RAG 额度</b> —— 它消耗的是「AI 症状解读」这个档位功能
     *       （34.9 及以上），而不是 RAG 问答配额；有解读权限的档位 RAG 本来就不限次数，
     *       再扣一次等于没扣，只会让用户觉得"点一次扣两次"。门禁由 healthy 侧按
     *       {@code healthAiAnalysis} 把关</li>
     *   <li>{@code uid} 由调用方带在请求体里，只用于日志归因</li>
     * </ul>
     *
     * <p>路径含 {@code internal}，网关已统一拒绝外部访问；服务间 Feign 走 Nacos 直连，不受影响。
     *
     * <p>解读是"一次性"的服务间调用，<b>不建会话、不落消息</b>，所以调用日志不关联会话。
     */
    @PostMapping("/internal/analyze")
    public Result<RagAskRespDTO> analyze(@RequestBody RagAnalyzeReqDTO requestParam) {
        if (requestParam.getQuestion() == null || requestParam.getQuestion().isBlank()) {
            throw new ClientException(AiErrorCodeEnum.AI_QUESTION_EMPTY);
        }
        RagAskReqDTO askParam = new RagAskReqDTO();
        askParam.setQuestion(requestParam.getQuestion());
        askParam.setSpecies(requestParam.getSpecies());
        Long uid = requestParam.getUid();
        long start = System.currentTimeMillis();
        try {
            RagAskService.AskResult result = ragAskService.ask(askParam);
            aiCallLogRecorder.recordSuccess(AiCallLogRecorder.SCENE_AI_ANALYZE, uid, result.usage(),
                    System.currentTimeMillis() - start);
            RagAskRespDTO resp = new RagAskRespDTO();
            resp.setAnswer(result.answer());
            resp.setSources(result.sources());
            resp.setHasKnowledge(result.hasKnowledge());
            return Results.success(resp);
        } catch (RuntimeException e) {
            // 不扣额度，所以没有额度要回退；但失败一样要记日志，否则解读的失败率无从统计
            aiCallLogRecorder.recordFailure(AiCallLogRecorder.SCENE_AI_ANALYZE, uid, errorCodeOf(e), e.getMessage(),
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    /**
     * SSE 流式问答：
     * <pre>
     * event: meta      → {"conversationId":123}  本次落在哪个会话（前端据此刷新左侧历史列表）
     * event: sources   → JSON 数组（引用的知识碎片，可先渲染"正在检索到的资料"）
     * event: thinking  → 模型的思考过程增量（思考模型才有；不落库，纯展示）
     * event: message   → 每生成一段就推一次（前端逐段追加，打字机效果）
     * event: done      → {"messageId":456,"latencyMs":3200,"tokenIn":..,"tokenOut":..}
     * [流结束]
     * </pre>
     * meta / done 是后加的事件：老前端只认 sources 与 message，多出来的事件会被忽略，因此是兼容的。
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody RagAskReqDTO requestParam) {
        validate(requestParam);
        Long uid = currentUid.require();
        boolean consumed = ragQuotaGuard.consumeOrThrow(uid);
        SseEmitter emitter = new SseEmitter(0L); // 0 = 不超时
        long start = System.currentTimeMillis();
        // 流式只有最后一帧带 usage，所以边收边覆盖，收流结束时才落库。
        // 用 AtomicReference 是因为它要在 lambda（可能跑在 reactor 线程）里被写入。
        AtomicReference<Usage> usage = new AtomicReference<>();
        // 流式回答是一段段吐出来的，得自己攒起来才能落库（前端拿到的只是碎片）
        StringBuilder answer = new StringBuilder();
        // 思考过程同样是一段段来的。
        // 这里只推给前端看、<b>不落库</b>：一旦存进历史消息，下一轮会被当成 AI 回答喂回模型，
        // 既污染上下文又白烧 token（Qwen 官方也不建议把 reasoning 回传）。
        StringBuilder thinking = new StringBuilder();
        Long conversationId = null;
        try {
            conversationId = aiConversationService.resolveConversation(uid, requestParam.getConversationId(),
                    requestParam.getQuestion());
            List<AiMessageDO> history = aiConversationService.historyForPrompt(conversationId);
            aiConversationService.appendUserMessage(uid, conversationId, requestParam.getQuestion());

            // 下面的 lambda 里要用，必须是 effectively final 的局部变量
            final Long convId = conversationId;
            // ① 混合检索 + 拼上下文（同步完成，先拿 sources）
            RagAskService.AskContext context = ragAskService.buildContext(requestParam, history);
            emitter.send(SseEmitter.event().name("meta").data(Map.of("conversationId", convId)));
            emitter.send(SseEmitter.event().name("sources").data(context.sources()));

            // ② 订阅流式生成，逐段推送，事件处理线程异步执行，不阻塞请求线程
            ragAskService.streamAnswer(context)
                    .doOnNext(response -> {
                        Usage chunkUsage = TokenUsageExtractor.usageOf(response);
                        if (chunkUsage != null) {
                            usage.set(chunkUsage);
                        }
                        // ① 思考帧：文本是空的，但 metadata 里带 reasoningContent（思考模型才有）。
                        //    实测 268 帧里 244 帧属于这种 —— 它们不是废帧，是模型在"想"。
                        //    推 event: thinking，前端可以做成可折叠的"推理过程"。
                        if (showThinking()) {
                            String reasoning = TokenUsageExtractor.reasoningOf(response);
                            if (reasoning != null && !reasoning.isEmpty()) {
                                thinking.append(reasoning);
                                try {
                                    emitter.send(SseEmitter.event().name("thinking").data(reasoning));
                                } catch (IOException e) {
                                    emitter.completeWithError(e);
                                    return;
                                }
                            }
                        }
                        // ② 正式答案帧：只推有文字的，多模态/思考模型的流里有大量无 text 的帧
                        //    （role、usage、结束帧）。注意判 null/空串而不是 hasText，
                        //    避免把换行、空格这类排版帧丢掉。
                        String text = TokenUsageExtractor.textOf(response);
                        if (text == null || text.isEmpty()) {
                            return;
                        }
                        answer.append(text);
                        try {
                            emitter.send(SseEmitter.event().name("message").data(text));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(() -> {
                        long latency = System.currentTimeMillis() - start;
                        // 先落库再发 done：前端拿到 done 就能立刻给这条回答点 👍/👎
                        Long messageId = saveAssistantMessage(uid, convId, answer.toString(), context,
                                usage.get(), latency);
                        aiCallLogRecorder.recordSuccess(AiCallLogRecorder.SCENE_ASK_STREAM, uid, usage.get(),
                                latency, convId, messageId);
                        sendDone(emitter, messageId, usage.get(), latency);
                        emitter.complete();
                    })
                    .doOnError(error -> {
                        // 流中途失败：这一次不算数，回退额度（已经吐给前端的片段不回收）
                        if (consumed) {
                            ragQuotaGuard.refund(uid);
                        }
                        aiCallLogRecorder.recordFailure(AiCallLogRecorder.SCENE_ASK_STREAM, uid, errorCodeOf(error),
                                error.getMessage(), System.currentTimeMillis() - start, convId);
                        emitter.completeWithError(error);
                    })
                    .subscribe();
        } catch (Exception e) {
            // 检索/建立连接阶段就失败：同样回退并记日志
            if (consumed) {
                ragQuotaGuard.refund(uid);
            }
            aiCallLogRecorder.recordFailure(AiCallLogRecorder.SCENE_ASK_STREAM, uid, errorCodeOf(e), e.getMessage(),
                    System.currentTimeMillis() - start, conversationId);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /**
     * 重建向量索引。
     * <p>
     * 运维动作：不扣额度、不要求登录；但同样记一条调用日志（耗时 + 成败），
     * 否则"索引重建花了多久、失败过几次"事后无从查起。
     * 有登录态就顺手带上 uid，没有也不报错。
     */
    @PostMapping("/index/build")
    public Result<Integer> rebuildIndex() {
        long start = System.currentTimeMillis();
        String errorMessage = null;
        try {
            return Results.success(ragIndexService.rebuildIndex());
        } catch (RuntimeException e) {
            errorMessage = e.getMessage();
            throw e;
        } finally {
            aiCallLogRecorder.recordIndexBuild(currentUid.orNull(), System.currentTimeMillis() - start, errorMessage);
        }
    }

    /**
     * 落库 AI 回答。失败只 warn：答案已经推给前端了，不能因为存不了历史就把它丢掉。
     */
    private Long saveAssistantMessage(Long uid, Long conversationId, String answer,
                                      RagAskService.AskContext context, Usage usage, long latency) {
        if (answer.isEmpty()) {
            return null;
        }
        try {
            return aiConversationService.appendAssistantMessage(uid, conversationId, answer,
                    context.sources(), context.hasKnowledge(), usage, latency);
        } catch (Exception e) {
            log.warn("AI 回答落库失败（答案已推给前端，仅历史缺失）：{}", e.getMessage());
            return null;
        }
    }

    /**
     * done 事件：把 messageId 与用量告诉前端。
     * messageId 为 null（没落库成功）时给 0 占位 —— Map.of 不接受 null 值。
     */
    private static void sendDone(SseEmitter emitter, Long messageId, Usage usage, long latency) {
        try {
            emitter.send(SseEmitter.event().name("done").data(Map.<String, Object>of(
                    "messageId", messageId == null ? 0L : messageId,
                    "latencyMs", latency,
                    "tokenIn", usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                    "tokenOut", usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens())));
        } catch (IOException e) {
            // 前端可能已经断开，发不出去很正常，不值得报错
            log.debug("done 事件发送失败（前端可能已断开）：{}", e.getMessage());
        }
    }

    /**
     * 是否把思考过程输出给前端。
     * <p>
     * 默认<b>开</b>：思考能力已经付过费了（思考 token 同样计费），把它展示出来能显著缓解
     * 单次问答 20+ 秒的等待焦虑；不想暴露推理内容就在 Nacos 里配
     * {@code llm.dashscope.chat.show-thinking: false}。
     * <p>
     * 注意这与 {@code llm.dashscope.chat.enable-thinking} 是两件事：
     * 前者管"给不给用户看"，后者管"模型想不想"（关掉思考才能真正省钱、降延迟）。
     */
    private boolean showThinking() {
        LlmProperties.LlmModelItem dashscope = llmProperties.getDashscope();
        LlmProperties.LlmSubProperties chat = dashscope == null ? null : dashscope.getChat();
        return chat == null || !Boolean.FALSE.equals(chat.getShowThinking());
    }

    private void validate(RagAskReqDTO requestParam) {
        if (requestParam.getQuestion() == null || requestParam.getQuestion().isBlank()) {
            throw new ClientException(AiErrorCodeEnum.AI_QUESTION_EMPTY);
        }
    }

    /**
     * 取异常里的业务错误码（{@link AbstractException#getErrorCode()}）；
     * 非业务异常退化成异常类名 —— 排障时"是什么错"比"错误消息"更好聚合。
     */
    private static String errorCodeOf(Throwable e) {
        if (e == null) {
            return null;
        }
        if (e instanceof AbstractException ae) {
            return ae.getErrorCode();
        }
        return e.getClass().getSimpleName();
    }
}