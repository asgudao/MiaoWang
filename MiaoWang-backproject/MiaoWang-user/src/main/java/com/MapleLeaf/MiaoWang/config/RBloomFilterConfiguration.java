package com.MapleLeaf.MiaoWang.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器配置
 */
@Slf4j
@Configuration
public class RBloomFilterConfiguration {

    /**
     * 期望插入量：按"实际量级"给，不是越大越好
     */
    private static final long EXPECTED_INSERTIONS = 1_000_000L;

    /**
     * 误判率
     */
    private static final double FALSE_PROBABILITY = 0.001;

    /**
     * 防止用户注册查询数据库的布隆过滤器
     * <p>
     * 参数与内存的关系（布隆过滤器的位数组大小 ≈ -n·ln(p)/(ln2)²）：
     * 100 万 + 0.1% → 约 14.4M bit ≈ 1.8MB Redis 内存；
     * 原来写的是 1 亿，同样误判率下实测位图占了 <b>169MB</b>，却只装了 5 个用户名。
     * <p>
     * 插入量超过预期只会让误判率变差（把没用过的用户名说成"已占用"，用户换个名字即可），
     * 不会漏判，所以宁可给小也不给大。
     * <p>
     * ️ 过滤器参数存在 Redis 里，改代码不会自动生效：Redis 中已存在的同名过滤器会沿用旧参数
     * （此时 tryInit 返回 false）。所以这里检测到参数不一致时**主动删掉重建**，
     * 重建后由 {@link com.MapleLeaf.MiaoWang.bootstrap.UserRegisterBloomFilterPreload} 把存量用户名重新灌回去。
     */
    @Bean
    public RBloomFilter<String> userRegisterCachePenetrationBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> cachePenetrationBloomFilter = redissonClient.getBloomFilter("userRegisterCachePenetrationBloomFilter");
        if (cachePenetrationBloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY)) {
            return cachePenetrationBloomFilter;
        }
        // 走到这里说明 Redis 里已有同名过滤器但参数不同（历史遗留的 1 亿参数就是这种情况）。
        // 参数改不了，只能整体删除重建——删是安全的：启动末尾的预热会把存量用户名全部重新灌进来。
        log.warn("布隆过滤器 [{}] 参数与当前配置不一致，删除并按「期望插入量 {} / 误判率 {}」重建（存量用户名由启动预热重新灌入）",
                cachePenetrationBloomFilter.getName(), EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        cachePenetrationBloomFilter.delete();
        if (!cachePenetrationBloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY)) {
            throw new IllegalStateException("布隆过滤器重建失败：" + cachePenetrationBloomFilter.getName());
        }
        return cachePenetrationBloomFilter;
    }
}