package com.MapleLeaf.MiaoWang.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 百炼（DashScope / MaaS 网关）对话模型装配 —— 只有 chat 一层，没有 embedding（本模块不做 RAG）。
 *
 * <h3>两层结构</h3>
 * <pre>
 *   DashScopeApi    = HTTP 客户端：请求发到哪、用哪把钥匙   → api-key + base-url
 *   ChatModel       = 模型包装器：用哪个模型、什么参数     → model 名、是否多模态、要不要思考
 * </pre>
 *
 * <h3>base-url 必须是"裸域名"</h3>
 * 框架内部按 {@code baseUrl + 路径} 拼完整地址（多模态走
 * {@code /api/v1/services/aigc/multimodal-generation/generation}）。
 * 配置里若已经带了 {@code /api/v1}，拼出来就是 {@code /api/v1/api/v1/...} → 404。
 * 所以 {@link #normalizeBaseUrl(String)} 会去掉结尾的 {@code /} 与 {@code /api/v1}。
 *
 * <h3>没配 key 也要能启动</h3>
 * {@code @ConditionalOnProperty} 让模型 Bean 只在存在 {@code llm.dashscope.chat.api-key} 时创建。
 * 没配 key 的环境（比如别人的机器）照样能起服务、能跑除聊天外的所有链路，
 * 聊天接口会返回业务错误码而不是启动失败。
 *
 * @see LlmProperties 对应 Nacos 的 {@code llm.*}
 */
@Configuration
@RequiredArgsConstructor
public class DashScopeConfig {

    @Autowired
    private LlmProperties llmProperties;

    /**
     * 第 1 层：chat 的 HTTP 客户端（只带 key 和地址）
     */
    @Bean("dashscopeChatApi")
    @ConditionalOnProperty(prefix = "llm.dashscope.chat", name = "api-key")
    public DashScopeApi dashscopeChatApi() {
        return baseBuilder(llmProperties.getDashscope().getChat()).build();
    }

    /**
     * 第 2 层：聊天模型 Bean —— 人设对话服务里 {@code @Qualifier("dashscopeChatModel")} 注入的就是它。
     *
     * <p>{@code multiModel(true)}：走多模态端点。qwen3.7-flash 这类模型必须开，
     * 否则框架走 text-generation 端点，网关报 "url error, please check url"。
     *
     * <p>思考开关：<b>本模块默认关</b>（见 {@link LlmProperties.ChatProperties#getEnableThinking()}）——
     * 宠物回复只有一两句，思考纯粹是延迟与成本。
     * 注意判空方向：只有显式配了 {@code true} 才打开，null 与 false 都算关闭。
     */
    @Bean("dashscopeChatModel")
    @ConditionalOnProperty(prefix = "llm.dashscope.chat", name = "api-key")
    public ChatModel dashscopeChatModel(@Qualifier("dashscopeChatApi") DashScopeApi dashscopeChatApi) {
        LlmProperties.ChatProperties chat = llmProperties.getDashscope().getChat();
        // 只有显式配置 true 才开启思考；不配 = 关（宠物短回复不需要思考，见类注释）
        boolean enableThinking = Boolean.TRUE.equals(chat.getEnableThinking());
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(chat.getModel())
                .multiModel(Boolean.TRUE.equals(chat.getMultiModel()))
                .enableThinking(enableThinking)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashscopeChatApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 两套共用的构造逻辑：api-key + 裸域名 base-url。
     * workspace-id 是可选开关：MaaS 网关报 403 Endpoint.AccessDenied 时填上，
     * 它会作为请求头 {@code X-DashScope-WorkSpace} 发出去。
     */
    private DashScopeApi.Builder baseBuilder(LlmProperties.ChatProperties chat) {
        DashScopeApi.Builder builder = DashScopeApi.builder()
                .apiKey(chat.getApiKey())
                .baseUrl(normalizeBaseUrl(chat.getBaseUrl()));
        if (StringUtils.hasText(chat.getWorkspaceId())) {
            builder.workSpaceId(chat.getWorkspaceId());
        }
        return builder;
    }

    /**
     * 把配置里的 base-url 规整为裸域名：去掉结尾的 {@code /} 和 {@code /api/v1}
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