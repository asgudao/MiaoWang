package com.MapleLeaf.MiaoWang;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeMultimodalEmbeddingAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeVideoAutoConfiguration;
import com.MapleLeaf.MiaoWang.config.EnvDiagnostic;
import com.MapleLeaf.MiaoWang.config.LlmProperties;
import com.MapleLeaf.MiaoWang.config.MilvusProperties;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * MiaoWang-ai-knowledge 启动类。
 *
 * <p>模型 Bean 全部由 {@code DashScopeConfig} 手动装配（读取 Nacos 上的 llm.* 自定义配置），
 * 因此排除框架自带的模型自动配置，避免同名 Bean 冲突
 * （典型：本项目的 dashscopeEmbeddingModel 与 DashScopeEmbeddingAutoConfiguration 撞名）。
 */
@EnableConfigurationProperties({LlmProperties.class, MilvusProperties.class})
@EnableFeignClients
@SpringBootApplication(exclude = {
        // ===== DashScope：chat / embedding 手动配置，其余能力本项目未使用 =====
        DashScopeChatAutoConfiguration.class,
        DashScopeEmbeddingAutoConfiguration.class,
        DashScopeMultimodalEmbeddingAutoConfiguration.class,
        DashScopeImageAutoConfiguration.class,
        DashScopeVideoAutoConfiguration.class,
        DashScopeRerankAutoConfiguration.class,
        DashScopeAudioSpeechAutoConfiguration.class,
        DashScopeAudioTranscriptionAutoConfiguration.class,
        // ===== OpenAI 协议：仅作 MaaS 网关兼容备用，不需要自动装配模型 =====
        OpenAiChatAutoConfiguration.class,
        OpenAiAudioSpeechAutoConfiguration.class,
        OpenAiAudioTranscriptionAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class,
        OpenAiImageAutoConfiguration.class,
        OpenAiModerationAutoConfiguration.class})
public class AiKnowledgeApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AiKnowledgeApplication.class);
        app.addListeners(new EnvDiagnostic());
        app.run(args);
    }
}