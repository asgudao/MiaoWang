package com.MapleLeaf.MiaoWang.support;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * 缓存观测计数
 *
 * <p>做缓存最怕"感觉快了"却拿不出数：这里把每一级缓存的命中与回源都记下来，
 * 通过 {@code GET /health/internal/cache-stats} 暴露，压测时可以直接看到命中率。
 *
 * <p>刻意用 {@link LongAdder} 而不是 AtomicLong：高并发计数场景下 LongAdder
 * 把写竞争分散到多个 cell，不会让计数本身变成瓶颈。
 */
@Component
public class HealthCacheStats {

    private final LongAdder ownershipL1Hit = new LongAdder();

    private final LongAdder ownershipL2Hit = new LongAdder();

    private final LongAdder ownershipLoad = new LongAdder();

    private final LongAdder ownershipRecheck = new LongAdder();

    private final LongAdder benefitL1Hit = new LongAdder();

    private final LongAdder benefitL2Hit = new LongAdder();

    private final LongAdder benefitLoad = new LongAdder();

    private final LongAdder benefitRecheck = new LongAdder();

    public void ownershipL1Hit() {
        ownershipL1Hit.increment();
    }

    public void ownershipL2Hit() {
        ownershipL2Hit.increment();
    }

    /**
     * 回源次数（= 真正落到 pet 服务的次数）
     */
    public void ownershipLoad() {
        ownershipLoad.increment();
    }

    /**
     * 归属校验不通过时的回源复核次数
     */
    public void ownershipRecheck() {
        ownershipRecheck.increment();
    }

    public void benefitL1Hit() {
        benefitL1Hit.increment();
    }

    public void benefitL2Hit() {
        benefitL2Hit.increment();
    }

    /**
     * 回源次数（= 真正落到 vip 服务的次数）
     */
    public void benefitLoad() {
        benefitLoad.increment();
    }

    /**
     * 权益校验"被判拒绝"时的回源复核次数
     */
    public void benefitRecheck() {
        benefitRecheck.increment();
    }

    /**
     * 当前计数快照
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ownership", part(ownershipL1Hit, ownershipL2Hit, ownershipLoad, ownershipRecheck));
        snapshot.put("benefit", part(benefitL1Hit, benefitL2Hit, benefitLoad, benefitRecheck));
        return snapshot;
    }

    private Map<String, Object> part(LongAdder l1, LongAdder l2, LongAdder load, LongAdder recheck) {
        long l1v = l1.sum();
        long l2v = l2.sum();
        long loadV = load.sum();
        long total = l1v + l2v + loadV;
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("l1Hit", l1v);
        part.put("l2Hit", l2v);
        part.put("load", loadV);
        part.put("recheck", recheck.sum());
        part.put("total", total);
        // 命中率按「没有回源」的比例算，保留一位小数便于直接念出来
        part.put("hitRate", total == 0 ? "-" : Math.round((l1v + l2v) * 1000.0 / total) / 10.0 + "%");
        return part;
    }
}