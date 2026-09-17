package com.MapleLeaf.MiaoWang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * 模型配置（对应 Nacos 的 MiaoWang-ai-common.yaml 中 llm 节点）。
 * 本项目只用到百炼 dashscope 的 chat（对话生成）与 embedding（文本向量化）。
 */
@Data
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private LlmModelItem dashscope;

    /**
     * dashscope 下每个能力的独立配置
     */
    @Data
    public static class LlmModelItem {
        private LlmSubProperties chat;
        private LlmSubProperties embedding;
    }

    /**
     * chat、embedding 的公共字段：api-key / model / base-url。
     * multi-model 仅 chat 使用：多模态模型（如 qwen3.7-flash）必须为 true，
     * 否则 DashScopeApi 会走 text-generation 端点而报 "url error"。
     * dimensions 仅 embedding 使用：向量维度，必须与 Milvus 集合维度一致。
     * workspace-id 可选：MaaS 网关报 403 Workspace endpoint access denied 时，
     * 填工作空间 ID（即 base-url 里的 llm-xxxxx 部分），会作为 X-DashScope-WorkSpace 头发送。
     */
    @Data
    public static class LlmSubProperties {
        private String apiKey;
        private String model;
        private String baseUrl;
        private Boolean multiModel;
        private Integer dimensions;
        private String workspaceId;

        /**
         * 输入侧单价（元/百万 token），仅用于把 token 折算成「估算成本」，不影响调用。
         * 不配置 → 调用日志的 estimated_cost 留空、统计接口显示"未配置单价"，不会编数字。
         */
        private BigDecimal priceInputPerMillion;

        /**
         * 输出侧单价（元/百万 token）
         */
        private BigDecimal priceOutputPerMillion;

        /**
         * 是否让思考模型"先想再答"（qwen3 系列默认会想）。
         * <b>不配 = 用模型默认行为</b>；配 false = 关掉思考，延迟通常能降一大截（思考 token 也是要计费的）。
         */
        private Boolean enableThinking;

        /**
         * 思考预算（token 上限）：越小越快。与 {@code enable-thinking=false} 二选一。
         */
        private Integer thinkingBudget;

        /**
         * 是否把<b>思考过程</b>输出给前端（SSE 的 {@code event: thinking} 与 {@code /ask} 的 reasoning 字段）。
         * <b>不配 = true</b>（思考能力已经付过费了，展示出来能显著缓解等待焦虑）；
         * 不想暴露推理内容就在 Nacos 里设为 false。
         */
        private Boolean showThinking;
    }
}