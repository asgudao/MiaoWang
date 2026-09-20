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
     * <p><b>为什么要探两个地方</b>：Spring AI 的元数据分两层 ——
     * 消息级（{@code AssistantMessage.metadata}）与响应级（{@code ChatResponse.metadata}）。
     * {@code reasoning_content} 落在哪一层不同版本实现不一样，所以两层都试。
     * <p>
     * 实测（spring-ai-alibaba 1.1.2.3 + qwen3.7-flash）：<b>两层都取不到</b> ——
     * 客户端虽然解析了 reasoning_content 字段，但没有透传到 Spring AI 的元数据上。
     * 此时"思考"只体现为：大量空文本帧 + 更多的输出 token（拉长了首字时间）。
     * 想真正拿到思考内容，需要换/升 SDK 或绕过客户端自行解析 SSE。
     */
    public static String reasoningOf(ChatResponse response) {
        Object reasoning = metadataValue(response, REASONING_KEY);
        return reasoning == null ? null : reasoning.toString();
    }

    /**
     * 排障用：把两层的 metadata key 列出来（只列 key，不含内容）。
     * "拿不到思考内容"时用它确认字段到底有没有透出来、叫什么名字。
     */
    public static String metadataKeysOf(ChatResponse response) {
        if (response == null) {
            return "response=null";
        }
        String messageKeys = "n/a";
        if (response.getResult() != null && response.getResult().getOutput() != null
                && response.getResult().getOutput().getMetadata() != null) {
            messageKeys = response.getResult().getOutput().getMetadata().keySet().toString();
        }
        String responseKeys = response.getMetadata() == null ? "null" : response.getMetadata().keySet().toString();
        return "message" + messageKeys + "  response" + responseKeys;
    }

    /**
     * 先消息级、再响应级地取一个 metadata 值
     */
    private static Object metadataValue(ChatResponse response, String key) {
        if (response == null) {
            return null;
        }
        if (response.getResult() != null && response.getResult().getOutput() != null) {
            var messageMetadata = response.getResult().getOutput().getMetadata();
            if (messageMetadata != null && messageMetadata.get(key) != null) {
                return messageMetadata.get(key);
            }
        }
        if (response.getMetadata() != null) {
            return response.getMetadata().get(key);
        }
        return null;
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