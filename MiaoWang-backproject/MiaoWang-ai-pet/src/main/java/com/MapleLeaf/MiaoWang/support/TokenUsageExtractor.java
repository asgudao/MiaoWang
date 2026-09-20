package com.MapleLeaf.MiaoWang.support;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * 从 Spring AI 的响应里"安全地"取文本与 token 用量
 *
 * <p>非流式调用看起来一条 {@code getText()} 就够了，但实际会踩两类空值：
 * <ul>
 *   <li>思考模型（qwen3 系列）在<b>思考开关打开</b>时，部分帧没有 text；
 *       即使关掉思考，结束帧的 result/output 也可能是 null</li>
 *   <li>模型被内容安全策略拦下时返回空 result</li>
 * </ul>
 * 判空逻辑集中在这里，聊天与问候两条链路共用同一套口径
 * （与 ai-knowledge 的同类实现同源，本模块只需要非流式用到的那两个方法）。
 */
public final class TokenUsageExtractor {

    private TokenUsageExtractor() {
    }

    /**
     * 取本次响应的正文；取不到返回 null（调用方自行决定是抛错还是当空串）
     */
    public static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 取本次响应携带的 token 用量；取不到返回 null。
     * <p>非流式调用一定拿得到（除非模型侧没返回 usage），拿不到时调用日志的 token 列留 NULL，
     * 而不是记 0 —— 记 0 会把"没数据"污染成"没花钱"。
     */
    public static Usage usageOf(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }
}