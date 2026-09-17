package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.support.HealthCacheStats;
import com.MapleLeaf.MiaoWang.support.HealthUserHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康模块内部观测接口
 *
 * <p>缓存改造最怕"感觉快了但拿不出数"，所以把命中率暴露出来：
 * 压测前后各看一次，就知道每级缓存挡掉了多少次跨服务调用。
 *
 * <p>安全边界：只返回聚合计数，不含任何用户数据；即便如此也要求登录，
 * 避免把内部指标暴露给未认证访客（正式上线应改走内网或运维鉴权）。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mapleleaf/miaowang/v1/health/internal")
public class HealthInternalController {

    private final HealthCacheStats cacheStats;

    /**
     * 缓存命中率快照
     */
    @GetMapping("/cache-stats")
    public Result<Map<String, Object>> cacheStats() {
        HealthUserHelper.currentUid();
        return Results.success(cacheStats.snapshot());
    }
}