package com.MapleLeaf.MiaoWang.support;


import cn.hutool.json.JSONUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.VipBenefitFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.HealthyErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.HealthRecordTypeEnum;
import com.MapleLeaf.MiaoWang.openfegin.vip.VipServiceFeign;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 健康档案权益校验
 *
 * <p>权益规则统一由 VIP 模块判定，本模块只消费结果，不自己判断会员状态：
 * 「每类记录条数上限 / 能否导出报告 / 能否用 AI 解读」三项都从
 * {@code /vip/internal/benefit/{uid}} 取，改价格或改额度只需要改 VIP 的档位表。
 *
 * <p>设计取舍：权益服务不可用时<b>直接报错，不放行</b>。因为这三项都是付费墙，
 * 失败时放行等于把会员权益白送；缓存故障则不影响可用性 —— 降级为直连 VIP。
 *
 * <p>性能上：权益那一跳实测 46ms，是首屏热路径上最贵的一次远程调用，
 * 且一次 {@code /overview} 会用到处方里的多个字段。现在按 uid 缓存，
 * 同一请求里多次取权益也不会重复调用（原实现里概览、额度校验、导出校验各调一次）。
 *
 * <p><b>关键取舍：缓存只用来加速「放行」，不参与「拒绝」。</b>
 * 判定为"超额/无权限"时会绕过缓存复核一次，避免用户刚开通会员却还被缓存里的
 * 旧档位挡在门外。被拒路径本来就低频，多一次调用换正确性划算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthBenefitResolver {

    /**
     * VIP 档位里代表「不限」的取值
     */
    public static final int UNLIMITED = -1;

    private final VipServiceFeign vipServiceFeign;

    private final StringRedisTemplate stringRedisTemplate;

    private final HealthCacheStats cacheStats;

    /**
     * L1：进程内缓存权益，同样借 Caffeine 的 {@code get(key, fn)} 做单飞
     */
    private final Cache<Long, VipBenefitFeignRespDTO> benefitL1 = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(HealthCacheKeys.BENEFIT_L1_SECONDS))
            .build();

    /**
     * 查询用户权益（走缓存）
     */
    public VipBenefitFeignRespDTO benefitOf(Long uid) {
        VipBenefitFeignRespDTO l1 = benefitL1.getIfPresent(uid);
        if (l1 != null) {
            cacheStats.benefitL1Hit();
            return l1;
        }
        return benefitL1.get(uid, key -> {
            VipBenefitFeignRespDTO fromRedis = readFromRedis(key);
            if (fromRedis != null) {
                cacheStats.benefitL2Hit();
                return fromRedis;
            }
            cacheStats.benefitLoad();
            return refresh(key);
        });
    }

    /**
     * 绕过缓存回源，并刷新两级缓存：校验被判「拒绝」时用它复核
     */
    public VipBenefitFeignRespDTO benefitOfFresh(Long uid) {
        cacheStats.benefitRecheck();
        VipBenefitFeignRespDTO fresh = fetchFromVipService(uid);
        benefitL1.put(uid, fresh);
        writeToRedis(uid, fresh);
        return fresh;
    }

    /**
     * 每类健康记录条数上限，-1=不限
     */
    public int recordLimit(Long uid) {
        return limitOf(benefitOf(uid));
    }

    /**
     * 写入前的额度校验
     *
     * @param uid          用户ID
     * @param recordType   记录类型（额度按类型分别计数）
     * @param currentCount 该宠物该类型的现有条数（逻辑删除的不算）
     */
    public void requireRecordQuota(Long uid, HealthRecordTypeEnum recordType, long currentCount) {
        if (withinQuota(limitOf(benefitOf(uid)), currentCount)) {
            return;
        }
        // 被拒是最伤体验的结论，回源复核一次再下结论（用户可能刚升级会员）
        int freshLimit = limitOf(benefitOfFresh(uid));
        if (withinQuota(freshLimit, currentCount)) {
            log.info("用户 {} 的{}记录额度经回源复核后放行（当前 {}，上限 {}）", uid, recordType.getDesc(), currentCount, freshLimit);
            return;
        }
        log.info("用户 {} 的{}记录条数已达上限 {}（当前 {}）", uid, recordType.getDesc(), freshLimit, currentCount);
        throw new ClientException(HealthyErrorCodeEnum.HEALTH_RECORD_LIMIT);
    }

    /**
     * 导出健康报告需要档位权限
     */
    public void requireExportAllowed(Long uid) {
        if (isFlagOn(benefitOf(uid).getHealthExport())) {
            return;
        }
        if (isFlagOn(benefitOfFresh(uid).getHealthExport())) {
            log.info("用户 {} 的导出权益经回源复核后放行", uid);
            return;
        }
        log.info("用户 {} 的档位不支持导出健康报告", uid);
        throw new ClientException(HealthyErrorCodeEnum.HEALTH_EXPORT_NOT_ALLOWED);
    }

    /**
     * 是否可用 AI 解读症状（预留给后续的 AI 解读接口）
     */
    public boolean aiAnalysisAllowed(Long uid) {
        return isFlagOn(benefitOf(uid).getHealthAiAnalysis());
    }

    /**
     * 是否处于会员状态（预留给后续的展示与提醒策略）
     */
    public boolean isVip(Long uid) {
        return Boolean.TRUE.equals(benefitOf(uid).getVip());
    }

    private boolean withinQuota(int limit, long currentCount) {
        return limit == UNLIMITED || currentCount < limit;
    }

    private int limitOf(VipBenefitFeignRespDTO benefit) {
        Integer limit = benefit.getHealthRecordLimit();
        if (limit == null) {
            // VIP 侧字段缺失时按不限处理并告警：健康档案是用户自己录的数据，
            // 因为上游配置缺失就把用户挡在门外，体验伤害比漏放一次更大
            log.warn("会员权益未返回健康记录上限，本次按不限处理，benefit={}", benefit);
            return UNLIMITED;
        }
        return limit;
    }

    private boolean isFlagOn(Integer flag) {
        return flag != null && flag == 1;
    }

    private VipBenefitFeignRespDTO refresh(Long uid) {
        VipBenefitFeignRespDTO benefit = fetchFromVipService(uid);
        writeToRedis(uid, benefit);
        return benefit;
    }

    private VipBenefitFeignRespDTO fetchFromVipService(Long uid) {
        Result<VipBenefitFeignRespDTO> result;
        try {
            result = vipServiceFeign.getBenefitByUid(uid);
        } catch (Exception e) {
            log.error("调用会员服务查询权益失败，uid={}", uid, e);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_VIP_SERVICE_ERROR);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.error("会员服务返回异常结果，uid={}，result={}", uid, result);
            throw new ServiceException(HealthyErrorCodeEnum.HEALTH_VIP_SERVICE_ERROR);
        }
        return result.getData();
    }

    private VipBenefitFeignRespDTO readFromRedis(Long uid) {
        try {
            String json = stringRedisTemplate.opsForValue().get(HealthCacheKeys.benefit(uid));
            if (json == null) {
                return null;
            }
            return JSONUtil.toBean(json, VipBenefitFeignRespDTO.class);
        } catch (Exception e) {
            log.warn("读取权益缓存失败，降级为回源，uid={}", uid, e);
            return null;
        }
    }

    private void writeToRedis(Long uid, VipBenefitFeignRespDTO benefit) {
        try {
            stringRedisTemplate.opsForValue().set(HealthCacheKeys.benefit(uid), JSONUtil.toJsonStr(benefit),
                    HealthCacheKeys.jitteredTtl(HealthCacheKeys.BENEFIT_L2_SECONDS));
        } catch (Exception e) {
            log.warn("写入权益缓存失败，忽略，uid={}", uid, e);
        }
    }
}