package com.MapleLeaf.MiaoWang.constant;

import java.time.Duration;

/**
 * 知识库常量
 */
public class KnowledgeConstant {

    private KnowledgeConstant() {
    }

    /**
     * 默认页码
     */
    public static final long DEFAULT_PAGE_NUM = 1L;

    /**
     * 默认每页条数
     */
    public static final long DEFAULT_PAGE_SIZE = 10L;

    /**
     * 每页最大条数：防止前端传 pageSize=100000 把整库一次拉走
     */
    public static final long MAX_PAGE_SIZE = 50L;

    /**
     * 浏览量计数 key 的存活时间
     * <p>
     * 兜底用：正常情况下增量会被定时任务写回并归零；万一写回一直失败，
     * key 也会自己过期，不会在 Redis 里留下永久垃圾。
     */
    public static final Duration VIEW_COUNT_KEY_TTL = Duration.ofDays(1);
}