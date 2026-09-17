package com.MapleLeaf.MiaoWang.task;

import com.MapleLeaf.MiaoWang.common.constant.RedisCacheConstant;
import com.MapleLeaf.MiaoWang.dao.mapper.KnowledgeFragmentMapper;
import com.MapleLeaf.MiaoWang.constant.KnowledgeConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 知识碎片浏览量：Redis 计数 → MySQL 合并写回
 * <p>
 * 为什么不在详情页直接 {@code UPDATE view_count = view_count + 1}：
 * 详情是公开高频读接口，那样等于"每读一次写一次库"，读放大直接变成写放大。
 * 这里读路径只对 Redis 做一次 INCR（O(1)、不碰数据库），再由本任务把窗口内的
 * 增量合并成一条 UPDATE 写回，把 N 次阅读压成每窗口 1 次写库。
 * <p>
 * 多实例同时跑也安全：增量用一段 Lua 脚本**原子地 GET + DEL** 取走
 * （不是"先 GET 再 DECRBY"——那样两个实例会读到同一份增量并各自写库，重复计数，
 * 还会把 Redis 里的值扣成负数）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeViewCountFlushTask {

    /**
     * 原子取走增量：返回取到的值并删除 key（GET 与 DEL 之间不会插入其它命令，脚本整体原子）
     * <p>
     * 用 Lua 而不是 Redis 6.2+ 的 GETDEL 命令：Lua 脚本在任意 Redis 版本上都能跑。
     */
    private static final RedisScript<String> TAKE_DELTA_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1]) "
                    + "if current then redis.call('DEL', KEYS[1]) end "
                    + "return current",
            String.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final KnowledgeFragmentMapper fragmentMapper;

    /**
     * 每 5 分钟合并写回一次（可用 Nacos 配置 miaowang.knowledge.view-flush-interval-ms 覆盖）
     */
    @Scheduled(fixedDelayString = "${miaowang.knowledge.view-flush-interval-ms:300000}")
    public void flushViewCount() {
        Set<String> keys = scanViewKeys();
        if (keys.isEmpty()) {
            return;
        }
        int flushed = 0;
        for (String key : keys) {
            long fragmentId = parseFragmentId(key);
            if (fragmentId <= 0) {
                continue;
            }
            long delta = parseDelta(takeDelta(key));
            if (delta <= 0) {
                continue;
            }
            try {
                fragmentMapper.addViewCount(fragmentId, delta);
                flushed++;
            } catch (Exception e) {
                // 写库失败：把刚取走的增量加回 Redis 并补上 TTL，留到下一轮重试（宁可重复计数，不可丢失）
                stringRedisTemplate.opsForValue().increment(key, delta);
                stringRedisTemplate.expire(key, KnowledgeConstant.VIEW_COUNT_KEY_TTL);
                log.warn("浏览量写回失败，已归还增量。fragmentId={}, delta={}", fragmentId, delta, e);
            }
        }
        if (flushed > 0) {
            log.info("浏览量合并写回完成：{} 条知识碎片", flushed);
        }
    }

    /**
     * 用 SCAN 而不是 KEYS：KEYS 会阻塞 Redis 单线程，生产上是禁忌
     */
    private Set<String> scanViewKeys() {
        Set<String> keys = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .match(RedisCacheConstant.KNOWLEDGE_VIEW_KEY + "*")
                .count(200)
                .build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        return keys;
    }

    private String takeDelta(String key) {
        return stringRedisTemplate.execute(TAKE_DELTA_SCRIPT, Collections.singletonList(key));
    }

    private long parseFragmentId(String key) {
        String idPart = key.substring(RedisCacheConstant.KNOWLEDGE_VIEW_KEY.length());
        try {
            return Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            // 不是本业务写的 key（前缀被污染），清掉避免每轮都扫到
            stringRedisTemplate.delete(key);
            return 0L;
        }
    }

    private long parseDelta(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}