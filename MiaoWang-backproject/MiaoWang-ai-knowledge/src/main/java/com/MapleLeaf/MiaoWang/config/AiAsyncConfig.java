package com.MapleLeaf.MiaoWang.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 模块的异步线程池配置
 */
@Configuration
public class AiAsyncConfig {

    /**
     * AI 日志专用线程池。
     * <p>
     * 为什么要单独一个池：
     * <ul>
     *   <li>写日志是"事后统计"用途，不该占用 Tomcat 请求线程 —— 用户等答案的时间越短越好；</li>
     *   <li>与业务线程隔离：日志库抖动时只该拖慢日志，不该拖慢问答。</li>
     * </ul>
     * 队列满时的策略是 {@link ThreadPoolExecutor.CallerRunsPolicy}：
     * 宁可让调用线程自己同步写（慢一点），也不丢日志 —— 统计口径不完整比慢更糟。
     */
    @Bean("aiLogExecutor")
    public Executor aiLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("ai-log-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 停机时把队列里已排队的日志写完，避免丢尾部数据
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}