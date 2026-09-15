package com.MapleLeaf.MiaoWang.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.text.DashScopeEmbeddingOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 百炼（DashScope/MaaS）模型配置：RAG 需要的两套能力 —— chat（生成）+ embedding（向量化）。
 *
 * <p>解决 spring-ai-alibaba 1.1.2.3 的路径拼接规则：
 * baseUrl 一律传「裸域名」，不带 /api/v1。DashScopeApi 在 multiModel=true 时会把路径
 * 硬编码为 /api/v1/services/aigc/multimodal-generation/generation，框架用 baseUrl + path
 * 拼完整地址，只有裸域名才不会出现 /api/v1/api/v1 双前缀。
 *
 * <p>chat 多模态模型（如 qwen3.7-flash）必须配置 multi-model: true，否则走 text-generation
 * 端点，网关报 "url error, please check url"。
 */
@Configuration
@RequiredArgsConstructor
public class DashScopeConfig {
    @Autowired
    private LlmProperties llmProperties;

    // ==================== chat（对话生成）====================

    @Bean("dashscopeChatApi")
    @ConditionalOnProperty(prefix = "llm.dashscope.chat", name = "api-key")
    public DashScopeApi dashscopeChatApi() {
        return baseBuilder(llmProperties.getDashscope().getChat()).build();
    }

    @Bean("dashscopeChatModel")
    @ConditionalOnProperty(prefix = "llm.dashscope.chat", name = "api-key")
    public ChatModel dashscopeChatModel(@Qualifier("dashscopeChatApi") DashScopeApi dashscopeChatApi) {
        LlmProperties.LlmSubProperties chat = llmProperties.getDashscope().getChat();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashscopeChatApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(chat.getModel())
                        .multiModel(Boolean.TRUE.equals(chat.getMultiModel()))
                        .build())
                .build();
    }

    // ==================== embedding（文本向量化，RAG 灌库/检索用）====================

    @Bean("dashscopeEmbeddingApi")
    @ConditionalOnProperty(prefix = "llm.dashscope.embedding", name = "api-key")
    public DashScopeApi dashscopeEmbeddingApi() {
        return baseBuilder(llmProperties.getDashscope().getEmbedding()).build();
    }

    @Bean("dashscopeEmbeddingModel")
    @ConditionalOnProperty(prefix = "llm.dashscope.embedding", name = "api-key")
    public EmbeddingModel dashscopeEmbeddingModel(@Qualifier("dashscopeEmbeddingApi") DashScopeApi dashscopeEmbeddingApi) {
        LlmProperties.LlmSubProperties embedding = llmProperties.getDashscope().getEmbedding();
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .model(embedding.getModel())
                // 维度必须与 milvus.dimension、Milvus 集合维度三者一致
                .dimensions(embedding.getDimensions())
                .build();
        return DashScopeEmbeddingModel.builder()
                .dashScopeApi(dashscopeEmbeddingApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 统一的 DashScopeApi 构造：api-key + 裸域名 base-url。
     * 若配置了 workspace-id，则额外携带 X-DashScope-WorkSpace 请求头
     * （MaaS 网关返回 403 Endpoint.AccessDenied / Workspace endpoint access denied 时可尝试）。
     */
    private DashScopeApi.Builder baseBuilder(LlmProperties.LlmSubProperties props) {
        DashScopeApi.Builder builder = DashScopeApi.builder()
                .apiKey(props.getApiKey())
                .baseUrl(normalizeBaseUrl(props.getBaseUrl()));
        if (StringUtils.hasText(props.getWorkspaceId())) {
            builder.workSpaceId(props.getWorkspaceId());
        }
        return builder;
    }

    /**
     * 把配置里的 base-url 规整为裸域名：去掉结尾的 / 和 /api/v1。
     */
    private String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/api/v1")) {
            url = url.substring(0, url.length() - "/api/v1".length());
        }
        return url;
    }
}