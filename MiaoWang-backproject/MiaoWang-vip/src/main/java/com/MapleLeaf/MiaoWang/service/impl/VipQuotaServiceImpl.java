package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.constant.VipRedisConstant;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipBizTypeEnum;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.VipQuotaService;
import com.MapleLeaf.MiaoWang.support.VipBenefitResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 每日额度实现：Redis INCR + 当天过期
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipQuotaServiceImpl implements VipQuotaService {

    /**
     * 按国内自然日切分额度（部署在别的时区也不会错算）
     */
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * key 过期时间多加一点缓冲：避免正好卡在零点的那次请求把计数提前清掉；
     * 因为 key 里带了日期，多留几分钟不会串到第二天
     */
    private static final long EXPIRE_BUFFER_SECONDS = 300L;

    private final VipBenefitResolver vipBenefitResolver;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public VipQuotaRespDTO queryQuota(Long uid, String biz) {
        VipBizTypeEnum bizType = requireBiz(biz);
        int limit = vipBenefitResolver.resolveDailyLimit(uid, bizType.getBiz());
        if (limit == VipBenefitResolver.UNLIMITED) {
            return unlimitedResult(bizType);
        }
        int used = currentUsed(uid, bizType);
        VipQuotaRespDTO result = buildResult(bizType, limit, used, used < limit);
        result.setMessage(used < limit ? "ok" : "今日次数已用完，开通会员可提升次数");
        return result;
    }

    @Override
    public VipQuotaRespDTO consumeQuota(Long uid, String biz) {
        VipBizTypeEnum bizType = requireBiz(biz);
        int limit = vipBenefitResolver.resolveDailyLimit(uid, bizType.getBiz());
        // 不限次数的档位不做计数，省掉无意义的 Redis 写入
        if (limit == VipBenefitResolver.UNLIMITED) {
            return unlimitedResult(bizType);
        }
        String key = quotaKey(uid, bizType);
        // 先自增再判断：INCR 本身原子，天然抗并发；超限时回滚，保证计数不会被顶超
        Long used = stringRedisTemplate.opsForValue().increment(key);
        if (used == null) {
            throw new ServiceException("额度计数服务异常，请稍后重试");
        }
        if (used == 1L) {
            // 当天第一次使用才设置过期时间，避免每次请求都刷新 TTL
            stringRedisTemplate.expire(key, secondsToEndOfDay(), TimeUnit.SECONDS);
        }
        if (used > limit) {
            stringRedisTemplate.opsForValue().decrement(key);
            log.info("用户 {} 的 {} 额度已用完（上限 {}）", uid, bizType.getDesc(), limit);
            VipQuotaRespDTO result = buildResult(bizType, limit, limit, false);
            result.setMessage("今日" + bizType.getDesc() + "次数已用完，开通会员可提升次数");
            return result;
        }
        VipQuotaRespDTO result = buildResult(bizType, limit, used.intValue(), true);
        result.setMessage("ok");
        return result;
    }

    private VipBizTypeEnum requireBiz(String biz) {
        VipBizTypeEnum bizType = VipBizTypeEnum.of(biz);
        if (bizType == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_QUOTA_BIZ_ERROR);
        }
        return bizType;
    }

    private int currentUsed(Long uid, VipBizTypeEnum bizType) {
        String value = stringRedisTemplate.opsForValue().get(quotaKey(uid, bizType));
        if (StrUtil.isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            // 计数 key 被异常写入时不放大问题，按已用 0 处理并告警
            log.warn("额度计数 key 值非法：{} -> {}", quotaKey(uid, bizType), value);
            return 0;
        }
    }

    private String quotaKey(Long uid, VipBizTypeEnum bizType) {
        return VipRedisConstant.buildQuotaKey(bizType.getBiz(), uid, currentDay());
    }

    private String currentDay() {
        return LocalDate.now(ZONE_ID).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private long secondsToEndOfDay() {
        ZonedDateTime now = ZonedDateTime.now(ZONE_ID);
        ZonedDateTime endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(ZONE_ID);
        return Duration.between(now, endOfDay).getSeconds() + EXPIRE_BUFFER_SECONDS;
    }

    private VipQuotaRespDTO unlimitedResult(VipBizTypeEnum bizType) {
        VipQuotaRespDTO result = new VipQuotaRespDTO();
        result.setBiz(bizType.getBiz());
        result.setBizDesc(bizType.getDesc());
        result.setLimit(VipBenefitResolver.UNLIMITED);
        result.setUsed(VipBenefitResolver.UNLIMITED);
        result.setRemaining(VipBenefitResolver.UNLIMITED);
        result.setAllowed(true);
        result.setMessage("会员不限次数");
        return result;
    }

    private VipQuotaRespDTO buildResult(VipBizTypeEnum bizType, int limit, int used, boolean allowed) {
        VipQuotaRespDTO result = new VipQuotaRespDTO();
        result.setBiz(bizType.getBiz());
        result.setBizDesc(bizType.getDesc());
        result.setLimit(limit);
        result.setUsed(used);
        result.setRemaining(Math.max(limit - used, 0));
        result.setAllowed(allowed);
        return result;
    }
}