package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.constant.VipRedisConstant;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipBizTypeEnum;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipQuotaPeriodEnum;
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
 * 额度实现：Redis INCR + 按周期归档过期
 * <ul>
 *   <li>DAY：key 归档到天（yyyyMMdd），次日零点过期</li>
 *   <li>MONTH：key 归档到月（yyyyMM），次月一号零点过期（免费档试用额度用这个）</li>
 * </ul>
 * key 自带归档刻度，所以永远不需要定时任务清理历史计数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipQuotaServiceImpl implements VipQuotaService {

    /**
     * 按国内自然日/自然月切分额度（部署在别的时区也不会错算）
     */
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * key 过期时间多加一点缓冲：避免正好卡在零点/月初的那次请求把计数提前清掉；
     * 因为 key 里带了归档刻度，多留几分钟不会串到下一个周期
     */
    private static final long EXPIRE_BUFFER_SECONDS = 300L;

    private final VipBenefitResolver vipBenefitResolver;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public VipQuotaRespDTO queryQuota(Long uid, String biz) {
        VipBizTypeEnum bizType = requireBiz(biz);
        VipBenefitResolver.QuotaSetting setting = vipBenefitResolver.resolveQuota(uid, bizType.getBiz());
        if (setting.limit() == VipBenefitResolver.UNLIMITED) {
            return unlimitedResult(bizType, setting);
        }
        int used = currentUsed(uid, bizType, setting.period());
        VipQuotaRespDTO result = buildResult(bizType, setting, used, used < setting.limit());
        result.setMessage(used < setting.limit()
                ? "ok"
                : periodPrefix(setting.period()) + bizType.getDesc() + "次数已用完，开通会员可提升次数");
        return result;
    }

    @Override
    public VipQuotaRespDTO consumeQuota(Long uid, String biz) {
        VipBizTypeEnum bizType = requireBiz(biz);
        VipBenefitResolver.QuotaSetting setting = vipBenefitResolver.resolveQuota(uid, bizType.getBiz());
        // 不限次数的档位不做计数，省掉无意义的 Redis 写入
        if (setting.limit() == VipBenefitResolver.UNLIMITED) {
            return unlimitedResult(bizType, setting);
        }
        String key = quotaKey(uid, bizType, setting.period());
        // 先自增再判断：INCR 本身原子，天然抗并发；超限时回滚，保证计数不会被顶超
        Long used = stringRedisTemplate.opsForValue().increment(key);
        if (used == null) {
            throw new ServiceException(VipErrorCodeEnum.VIP_QUOTA_CALC_ERROR);
        }
        if (used == 1L) {
            // 本周期第一次使用才设置过期时间，避免每次请求都刷新 TTL
            stringRedisTemplate.expire(key, secondsToPeriodEnd(setting.period()), TimeUnit.SECONDS);
        }
        if (used > setting.limit()) {
            stringRedisTemplate.opsForValue().decrement(key);
            log.info("用户 {} 的 {} 额度已用完（{}/{}，上限 {}）", uid, bizType.getDesc(),
                    periodPrefix(setting.period()), setting.period(), setting.limit());
            VipQuotaRespDTO result = buildResult(bizType, setting, setting.limit(), false);
            result.setMessage(periodPrefix(setting.period()) + bizType.getDesc() + "次数已用完，开通会员可提升次数");
            return result;
        }
        VipQuotaRespDTO result = buildResult(bizType, setting, used.intValue(), true);
        result.setMessage("ok");
        return result;
    }

    @Override
    public void refundQuota(Long uid, String biz) {
        VipBizTypeEnum bizType = requireBiz(biz);
        VipBenefitResolver.QuotaSetting setting = vipBenefitResolver.resolveQuota(uid, bizType.getBiz());
        // 不限次数的档位从不计数，没有可回退的
        if (setting.limit() == VipBenefitResolver.UNLIMITED) {
            return;
        }
        if (currentUsed(uid, bizType, setting.period()) <= 0) {
            // 没扣过，或计数 key 已过期（跨周期了）—— 直接忽略，避免把计数回退成负数
            return;
        }
        String key = quotaKey(uid, bizType, setting.period());
        Long left = stringRedisTemplate.opsForValue().decrement(key);
        if (left != null && left < 0) {
            // 并发下重复回退的保护：计数不允许为负
            stringRedisTemplate.opsForValue().increment(key);
            return;
        }
        log.info("用户 {} 的 {} 额度已回退（{}，剩余计数 {}）", uid, bizType.getDesc(),
                periodPrefix(setting.period()), left);
    }

    private VipBizTypeEnum requireBiz(String biz) {
        VipBizTypeEnum bizType = VipBizTypeEnum.of(biz);
        if (bizType == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_QUOTA_BIZ_ERROR);
        }
        return bizType;
    }

    private int currentUsed(Long uid, VipBizTypeEnum bizType, String period) {
        String key = quotaKey(uid, bizType, period);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            // 计数 key 被异常写入时不放大问题，按已用 0 处理并告警
            log.warn("额度计数 key 值非法：{} -> {}", key, value);
            return 0;
        }
    }

    private String quotaKey(Long uid, VipBizTypeEnum bizType, String period) {
        return VipRedisConstant.buildQuotaKey(bizType.getBiz(), period, uid, bucketOf(period));
    }

    /**
     * 归档刻度：DAY → yyyyMMdd，MONTH → yyyyMM
     */
    private String bucketOf(String period) {
        LocalDate today = LocalDate.now(ZONE_ID);
        return VipQuotaPeriodEnum.MONTH.getPeriod().equals(period)
                ? today.format(MONTH_FORMATTER)
                : today.format(DAY_FORMATTER);
    }

    /**
     * 距离本周期结束还剩多少秒（DAY=次日零点，MONTH=次月一号零点）
     */
    private long secondsToPeriodEnd(String period) {
        ZonedDateTime now = ZonedDateTime.now(ZONE_ID);
        ZonedDateTime periodEnd = VipQuotaPeriodEnum.MONTH.getPeriod().equals(period)
                ? now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(ZONE_ID)
                : now.toLocalDate().plusDays(1).atStartOfDay(ZONE_ID);
        return Duration.between(now, periodEnd).getSeconds() + EXPIRE_BUFFER_SECONDS;
    }

    private String periodPrefix(String period) {
        return VipQuotaPeriodEnum.MONTH.getPeriod().equals(period) ? "本月" : "今日";
    }

    private VipQuotaRespDTO unlimitedResult(VipBizTypeEnum bizType, VipBenefitResolver.QuotaSetting setting) {
        VipQuotaRespDTO result = new VipQuotaRespDTO();
        fillBizInfo(result, bizType, setting);
        result.setLimit(VipBenefitResolver.UNLIMITED);
        result.setUsed(VipBenefitResolver.UNLIMITED);
        result.setRemaining(VipBenefitResolver.UNLIMITED);
        result.setAllowed(true);
        result.setMessage("会员不限次数");
        return result;
    }

    private VipQuotaRespDTO buildResult(VipBizTypeEnum bizType, VipBenefitResolver.QuotaSetting setting,
                                        int used, boolean allowed) {
        VipQuotaRespDTO result = new VipQuotaRespDTO();
        fillBizInfo(result, bizType, setting);
        result.setLimit(setting.limit());
        result.setUsed(used);
        result.setRemaining(Math.max(setting.limit() - used, 0));
        result.setAllowed(allowed);
        return result;
    }

    private void fillBizInfo(VipQuotaRespDTO result, VipBizTypeEnum bizType, VipBenefitResolver.QuotaSetting setting) {
        result.setBiz(bizType.getBiz());
        result.setBizDesc(bizType.getDesc());
        result.setPeriod(setting.period());
        VipQuotaPeriodEnum periodEnum = VipQuotaPeriodEnum.ofOrDefault(setting.period());
        result.setPeriodDesc(periodEnum.getDesc());
    }
}