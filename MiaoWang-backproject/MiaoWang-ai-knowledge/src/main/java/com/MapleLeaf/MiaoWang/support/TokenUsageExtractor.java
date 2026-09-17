package com.MapleLeaf.MiaoWang.support;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 从 Spring AI 的响应里"安全地"取文本与 token 用量。
 *
 * <p>为什么需要它：思考模型（如 qwen3.7-flash）的流里有大量<b>没有 text 的帧</b>
 * （思考过程、role 帧、usage 帧、结束帧），而且这些帧的 result / output 也可能是 null。
 * 直接 {@code response.getResult().getOutput().getText()} 会 NPE 打断整条流。
 * 判空逻辑集中在这里，问答与日志两条链路共用一套口径。
 */
public final class TokenUsageExtractor {

    /**
     * 思考内容在响应 metadata 里的 key。
     * spring-ai-alibaba 的 DashScopeChatModel 把模型返回的 {@code reasoning_content}
     * 放进了 {@code ChatResponse.metadata}，就用这个 key 取。
     */
    public static final String REASONING_KEY = "reasoningContent";

    private TokenUsageExtractor() {
    }

    /**
     * 取本帧的"思考过程"（思考模型才有）；取不到返回 null。
     *
     * <p><b>为什么流式也拿得到</b>：DashScopeChatModel 内部把每个流式 chunk 先转成
     * {@code ChatCompletion}，再走与非流式同一条 {@code toChatResponse} 转换路径，
     * 所以 reasoning_content 同样落在每帧的 metadata 上 ——
     * 那些"文本为空"的帧不是废帧，它们正是模型在思考。
     */
    public static String reasoningOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Object reasoning = response.getMetadata().get(REASONING_KEY);
        return reasoning == null ? null : reasoning.toString();
    }

    /**
     * 取本帧的增量文本；取不到返回 null（调用方自行决定跳过还是当空串）
     */
    public static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 取本帧携带的 token 用量；取不到返回 null。
     * <p>
     * 注意两者的差异：<b>非流式一定拿得到</b>；
     * <b>流式只有最后一帧带 usage</b>，所以流式链路要"边收边覆盖"，收流结束时才落库。
     */
    public static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }
}