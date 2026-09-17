package com.MapleLeaf.MiaoWang.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聚合查询线程池
 *
 * <p>概览接口里几笔小查询互不依赖，串行执行等于把各自的往返时间相加；
 * 并行后墙钟时间约等于最慢的那一笔。
 *
 * <p>参数刻意保守（核心 4 / 最大 8）：每个并行任务都会占一条数据库连接，
 * 而 Hikari 默认池大小是 10 —— 线程池开得比连接池还大，只会让请求堵在等连接上。
 * 队列满时用 {@link ThreadPoolExecutor.CallerRunsPolicy} 退化成串行执行，
 * 保证「变慢」而不是「失败」：高并发下宁可自己扛，也不丢任务、不雪崩。
 */
@Configuration
public class HealthQueryConfig {

    /**
     * 概览并行查询专用线程池
     */
    @Bean(name = "healthQueryExecutor", destroyMethod = "shutdown")
    public ExecutorService healthQueryExecutor() {
        AtomicInteger seq = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                4,
                8,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "health-query-" + seq.incrementAndGet());
                    // 守护线程：服务关闭时不会被这些线程拖住
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }
}