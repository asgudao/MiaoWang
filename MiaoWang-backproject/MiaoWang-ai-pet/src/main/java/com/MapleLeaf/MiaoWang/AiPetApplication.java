package com.MapleLeaf.MiaoWang;

import com.MapleLeaf.MiaoWang.config.AiPetProperties;
import com.MapleLeaf.MiaoWang.config.LlmProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeMultimodalEmbeddingAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeVideoAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * MiaoWang-ai-pet 启动类 —— AI 电子宠物（人设对话 + 打开首页的自动问候）。
 *
 * <h3>和 ai-knowledge 的关系</h3>
 * 两个模块都调大模型，但用途完全不同：
 * <ul>
 *   <li>ai-knowledge：<b>知识问答</b>，检索知识库后让模型"照资料回答"（RAG，需要 embedding + Milvus）</li>
 *   <li>ai-pet：<b>角色扮演</b>，把宠物档案拼成人设提示词让模型"演这只宠物"（<b>不做 RAG</b>，纯人设 + 多轮记忆）</li>
 * </ul>
 * 所以本模块只装配 chat 模型，不引入 embedding / Milvus。
 *
 * <h3>为什么排除 DashScope 的自动配置</h3>
 * 模型 Bean 全部由 {@link com.MapleLeaf.MiaoWang.config.DashScopeConfig} 手工装配
 * （读的是本项目自定义的 {@code llm.*} 而不是框架约定的 {@code spring.ai.dashscope.*}）。
 * 不排除的话，框架的自动配置会再创建一个同类模型 Bean，容易出现"找到多个候选 Bean"或同名冲突；
 * 这与 ai-knowledge 的处理保持一致。
 *
 * <h3>注册进来的基础设施</h3>
 * <ul>
 *   <li>{@code @EnableFeignClients}：调 pet 服务取宠物档案、调 vip 服务扣"宠物聊天"额度</li>
 *   <li>{@code @MapperScan}：会话 / 消息 / 调用日志三张表的 Mapper</li>
 *   <li>{@link AiPetProperties}：本模块自己的可调参数（问候开关、历史预算、提问长度）</li>
 * </ul>
 */
@EnableConfigurationProperties({LlmProperties.class, AiPetProperties.class})
@EnableFeignClients
@MapperScan(basePackages = "com.MapleLeaf.MiaoWang.dao")
@SpringBootApplication(exclude = {
        // ===== DashScope：只需要 chat，其余能力本项目未使用 =====
        DashScopeChatAutoConfiguration.class,
        DashScopeEmbeddingAutoConfiguration.class,
        DashScopeMultimodalEmbeddingAutoConfiguration.class,
        DashScopeImageAutoConfiguration.class,
        DashScopeVideoAutoConfiguration.class,
        DashScopeRerankAutoConfiguration.class,
        DashScopeAudioSpeechAutoConfiguration.class,
        DashScopeAudioTranscriptionAutoConfiguration.class})
public class AiPetApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPetApplication.class, args);
    }
}