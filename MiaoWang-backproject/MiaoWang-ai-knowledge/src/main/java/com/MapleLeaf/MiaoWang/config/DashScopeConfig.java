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
 * 百炼（DashScope / MaaS 网关）模型配置 —— RAG 需要的两套模型都在这里手工装配。
 *
 * <h3>一、先搞懂"两层"结构（最容易懵的地方）</h3>
 * <pre>
 *   DashScopeApi    = HTTP 客户端：只管「请求发到哪、用哪把钥匙」
 *                     → 配 api-key + base-url
 *   ChatModel       = 模型包装器：只管「用哪个模型、什么参数」
 *                     → 配 model 名（qwen3.7-flash）、是否多模态
 *   EmbeddingModel  = 同上，配 text-embedding-v4、向量维度 1024
 *
 *   所以它们成对出现：
 *       dashscopeChatApi      + dashscopeChatModel
 *       dashscopeEmbeddingApi + dashscopeEmbeddingModel
 * </pre>
 *
 * <h3>二、为什么 base-url 要传"裸域名"</h3>
 * 框架内部是 baseUrl + 路径 拼出完整地址的：
 * <pre>
 *   multiModel = true  → /api/v1/services/aigc/multimodal-generation/generation
 *   multiModel = false → /api/v1/services/aigc/text-generation/generation
 * </pre>
 * 如果 base-url 里已经带了 /api/v1，拼出来就成了 /api/v1/api/v1/... → 404。
 * 所以 normalizeBaseUrl() 会把结尾的 / 和 /api/v1 去掉。
 *
 * <h3>三、为什么不用框架的自动配置</h3>
 * 本项目的配置放在自定义的 llm.* 下（不是 spring.ai.dashscope.*），所以 Bean 必须自己 new；
 * 同时 AiKnowledgeApplication 里排除了框架的 DashScope*AutoConfiguration，
 * 否则会和这里的同名 Bean 冲突（"bean already defined"）。
 *
 * @see LlmProperties 配置类（对应 Nacos 里的 llm.*）
 */
@Configuration
@RequiredArgsConstructor
public class DashScopeConfig {
    @Autowired
    private LlmProperties llmProperties;

    // ==================== chat（对话生成）====================

    /**
     * 第 1 层：chat 的 HTTP 客户端（只带 key 和地址）。
     * <p>
     * @ConditionalOnProperty 的意思是：只有配置里存在 llm.dashscope.chat.api-key 才创建这个 Bean。
     * 没配就不创建 —— 这样没配 key 的环境也能正常启动，不会因为找不到 Bean 而启动失败。
     */
    @Bean("dashscopeChatApi")
    @ConditionalOnProperty(prefix = "llm.dashscope.chat", name = "api-key")
    public DashScopeApi dashscopeChatApi() {
        return baseBuilder(llmProperties.getDashscope().getChat()).build();
    }

    /**
     * 第 2 层：聊天模型 Bean —— RagAskService 里 @Qualifier("dashscopeChatModel") 注入的就是它。
     * <p>
     * multiModel(true)：走多模态端点。qwen3.7-flash 这类模型必须开，
     * 否则框架走 text-generation 端点，网关报 "url error, please check url"。
     */
    @Bean("dashscopeChatModel")
    @ConditionalOnProperty(prefix = "llm.dashscope.chat", name = "api-key")
    public ChatModel dashscopeChatModel(@Qualifier("dashscopeChatApi") DashScopeApi dashscopeChatApi) {
        LlmProperties.LlmSubProperties chat = llmProperties.getDashscope().getChat();
        // 思考相关的两个参数是"可选下发"：
        //   不配 → 不下发，保持模型默认行为（避免无意中改变线上效果）
        //   配了 → 覆盖默认。enable-thinking=false 或 thinking-budget=512 都能明显降延迟
        var optionsBuilder = DashScopeChatOptions.builder()
                .model(chat.getModel())
                .multiModel(Boolean.TRUE.equals(chat.getMultiModel()));
        if (chat.getEnableThinking() != null) {
            optionsBuilder.enableThinking(chat.getEnableThinking());
        }
        if (chat.getThinkingBudget() != null) {
            optionsBuilder.thinkingBudget(chat.getThinkingBudget());
        }
        return DashScopeChatModel.builder()
                .dashScopeApi(dashscopeChatApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    // ==================== embedding（文本向量化：灌库和检索都要用）====================

    /**
     * 第 1 层：embedding 的 HTTP 客户端（key + 地址）。
     */
    @Bean("dashscopeEmbeddingApi")
    @ConditionalOnProperty(prefix = "llm.dashscope.embedding", name = "api-key")
    public DashScopeApi dashscopeEmbeddingApi() {
        return baseBuilder(llmProperties.getDashscope().getEmbedding()).build();
    }

    /**
     * 第 2 层：向量模型 Bean —— KnowledgeRetriever / RagIndexService 里注入的就是它。
     * <p>
     * dimensions(1024) 必须与 Milvus 集合维度一致，否则写入 Milvus 会直接失败。
     * 灌库时一次最多传 10 条文字（见 RagIndexService.BATCH_SIZE）。
     */
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
     * 两套 Api 共用的构造逻辑：api-key + 裸域名 base-url。
     * <p>
     * workspace-id 是可选开关：如果 MaaS 网关报
     * 403 Endpoint.AccessDenied / Workspace endpoint access denied，
     * 就填上工作空间 ID，它会作为请求头 X-DashScope-WorkSpace 发出去。
     * 当前 Nacos 里这一项是注释状态（说明不需要它）。
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