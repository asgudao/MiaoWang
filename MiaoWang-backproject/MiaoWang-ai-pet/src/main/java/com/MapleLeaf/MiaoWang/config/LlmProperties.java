package com.MapleLeaf.MiaoWang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 模型配置（与 ai-knowledge 共用 Nacos 上的 {@code MiaoWang-ai-common.yaml} 里同一个 {@code llm} 节点）。
 *
 * <p>本模块<b>只声明 chat</b>：电子宠物对话是纯人设扮演，不做 RAG，也就用不到 embedding。
 * 配置里多出来的 {@code llm.dashscope.embedding.*} 会被 Spring Boot 直接忽略（未知字段不报错），
 * 所以两个模块可以共用同一份 Nacos 配置而各自只绑自己需要的部分。
 */
@Data
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    private LlmModelItem dashscope;

    /**
     * dashscope 下的能力分组（本模块只用 chat）
     */
    @Data
    public static class LlmModelItem {
        private ChatProperties chat;
    }

    /**
     * chat 的配置项
     */
    @Data
    public static class ChatProperties {

        /**
         * API Key（Nacos 里是 {@code ${AI_DASHSCOPE_API_KEY}}，从环境变量注入）。
         * 取不到时 {@code DashScopeConfig} 里的 {@code @ConditionalOnProperty} 会让模型 Bean 不创建，
         * 服务仍能启动 —— 只是聊天接口会返回"它现在没接上话"。
         */
        private String apiKey;

        /**
         * 模型名（MaaS 网关别名，如 qwen3.7-flash）
         */
        private String model;

        /**
         * 访问地址（裸域名，不带 /api/v1：框架内部会自己拼路径，带了就变成 /api/v1/api/v1/… → 404）
         */
        private String baseUrl;

        /**
         * 是否多模态模型（qwen3.7-flash 这类必须为 true，否则框架走 text-generation 端点报 "url error"）
         */
        private Boolean multiModel;

        /**
         * 可选：MaaS 网关报 403 Workspace endpoint access denied 时填工作空间 ID
         */
        private String workspaceId;

        /**
         * 输入侧单价（元/百万 token），仅用于把 token 折算成"估算成本"，不配置就让成本留空、不编数字
         */
        private BigDecimal priceInputPerMillion;

        /**
         * 输出侧单价（元/百万 token）
         */
        private BigDecimal priceOutputPerMillion;

        /**
         * 是否让思考模型"先想再答"。
         *
         * <p><b>本模块默认 false</b>（与 ai-knowledge 的"不配 = 跟随模型默认"不同）：
         * 电子宠物的回复只有一两句，思考过程对这种短回复没有任何质量收益，
         * 却会把单次响应从 2~4 秒拉到十几秒（实测 ai-knowledge 一次 19 秒里绝大部分是思考帧）。
         * 想对比效果就在 Nacos 里显式配 {@code llm.dashscope.chat.enable-thinking: true}。
         */
        private Boolean enableThinking;

        /**
         * 思考预算（token 上限）。开了思考又想压延迟时用它，比整个关掉更温和。
         */
        private Integer thinkingBudget;
    }
}