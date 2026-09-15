package com.MapleLeaf.MiaoWang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
    }
}