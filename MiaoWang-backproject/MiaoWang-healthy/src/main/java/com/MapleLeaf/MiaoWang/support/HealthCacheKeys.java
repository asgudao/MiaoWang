package com.MapleLeaf.MiaoWang.support;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 健康模块缓存约定
 *
 * <p>缓存的目的不是"少查一次库"，而是把首屏热路径上的<b>跨服务调用</b>干掉：
 * 一次 {@code /overview} 原本要同步等 pet（归属校验）和 vip（权益）两跳，
 * 实测占掉整请求耗时的一半以上。
 *
 * <p>两级缓存的分工：
 * <ul>
 *   <li>L1 = 进程内 Caffeine：纳秒级、天然单飞（同 key 只有一个线程回源），
 *       但多实例之间不共享，所以 TTL 取短值，让数据尽快回到 L2 的共识上</li>
 *   <li>L2 = Redis：多实例共享，挡住"每个实例各自回源一次"的重复流量</li>
 * </ul>
 *
 * <p>TTL 一律加随机抖动：固定过期时间会让一批 key 在同一秒集体失效，
 * 从而把压力一股脑推给下游（缓存雪崩的经典形态）。
 */
public final class HealthCacheKeys {

    private HealthCacheKeys() {
    }

    /**
     * 用户宠物归属，value = 宠物列表 JSON（空数组也缓存，避免"没有宠物"的用户每次都穿透到 pet）
     */
    public static final String OWNERSHIP_PREFIX = "MiaoWang:health:ownership:";

    /**
     * 会员权益，value = 权益 JSON
     */
    public static final String BENEFIT_PREFIX = "MiaoWang:health:benefit:";

    /**
     * 归属：L1 短 TTL。宠物是低频变更数据，但"刚建好宠物马上录第一条"必须能立刻通过，
     * 所以靠 10 秒这个很短的窗口 + 拒绝时回源复核来兜底
     */
    public static final long OWNERSHIP_L1_SECONDS = 10;

    /**
     * 归属：L2 TTL
     */
    public static final long OWNERSHIP_L2_SECONDS = 30;

    /**
     * 权益：L1 TTL。权益是付费墙，用户刚升级会员就该尽快生效
     */
    public static final long BENEFIT_L1_SECONDS = 30;

    /**
     * 权益：L2 TTL
     */
    public static final long BENEFIT_L2_SECONDS = 60;

    public static String ownership(Long uid) {
        return OWNERSHIP_PREFIX + uid;
    }

    public static String benefit(Long uid) {
        return BENEFIT_PREFIX + uid;
    }

    /**
     * 带随机抖动的 TTL：在 [base, base + base/5) 秒之间取值
     */
    public static Duration jitteredTtl(long baseSeconds) {
        long extra = ThreadLocalRandom.current().nextLong(Math.max(1, baseSeconds / 5));
        return Duration.ofSeconds(baseSeconds + extra);
    }
}