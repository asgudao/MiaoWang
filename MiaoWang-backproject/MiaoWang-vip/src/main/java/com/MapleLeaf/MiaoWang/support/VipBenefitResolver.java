package com.MapleLeaf.MiaoWang.support;


import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.UserVipDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPlanDO;
import com.MapleLeaf.MiaoWang.dao.mapper.UserVipMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPlanMapper;
import com.MapleLeaf.MiaoWang.enums.VipBizTypeEnum;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPlanCodeEnum;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 权益解析器：把「用户生效中的档位」合并成一份最终权益
 *
 * <p>合并口径（重要，改权益先看这里）：
 * <ol>
 *   <li>每个维度独立取最大值 —— 宠物数量、RAG 次数、聊天次数互不影响</li>
 *   <li>{@link #UNLIMITED}（-1）优先级最高：任一档位不限次数，该维度就不限</li>
 *   <li>无生效会员时回落 FREE 档权益（读库，不在代码里写死次数）</li>
 * </ol>
 *
 * <p>好处：以后加「宠物 + AI」组合包只需要在档位表插一条数据，判定逻辑不用动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VipBenefitResolver {

    /**
     * 不限次数标记
     */
    public static final int UNLIMITED = -1;

    private final VipPlanMapper vipPlanMapper;

    private final UserVipMapper userVipMapper;

    /**
     * 解析用户当前权益（未登录/uid 为空时返回免费档权益）
     */
    public VipBenefit resolve(Long uid) {
        VipBenefit benefit = new VipBenefit();
        benefit.setVip(false);
        benefit.setPlanNames(Collections.emptyList());
        // 免费档兜底：优先读库，库中缺失时用代码兜底值，保证服务不因配置缺失而不可用
        VipPlanDO freePlan = queryPlan(VipPlanCodeEnum.FREE.getCode());
        if (freePlan == null) {
            log.warn("会员档位表中缺少 FREE 档配置，已使用代码兜底值（宠物各1只 / RAG 3次 / 聊天 5次）");
        }
        benefit.setPetLimitPerType(freePlan != null ? freePlan.getPetLimitPerType() : 1);
        benefit.setRagDailyLimit(freePlan != null ? freePlan.getRagDailyLimit() : 3);
        benefit.setChatDailyLimit(freePlan != null ? freePlan.getChatDailyLimit() : 5);
        if (uid == null) {
            return benefit;
        }
        // 生效中 = 状态正常 + 未删除 + 未过期
        List<UserVipDO> memberships = userVipMapper.selectList(Wrappers.lambdaQuery(UserVipDO.class)
                .eq(UserVipDO::getUid, uid)
                .eq(UserVipDO::getStatus, 1)
                .eq(UserVipDO::getDelFlag, 0)
                .gt(UserVipDO::getExpireTime, new Date()));
        if (memberships == null || memberships.isEmpty()) {
            return benefit;
        }
        List<String> planNames = new ArrayList<>();
        Date latestExpireTime = null;
        for (UserVipDO membership : memberships) {
            VipPlanDO plan = queryPlan(membership.getPlanCode());
            if (plan == null) {
                log.warn("用户 {} 持有档位 {} 但档位配置已不存在，该档位权益被忽略", uid, membership.getPlanCode());
                continue;
            }
            benefit.setPetLimitPerType(mergeLimit(benefit.getPetLimitPerType(), plan.getPetLimitPerType()));
            benefit.setRagDailyLimit(mergeLimit(benefit.getRagDailyLimit(), plan.getRagDailyLimit()));
            benefit.setChatDailyLimit(mergeLimit(benefit.getChatDailyLimit(), plan.getChatDailyLimit()));
            planNames.add(plan.getPlanName());
            if (latestExpireTime == null || membership.getExpireTime().after(latestExpireTime)) {
                latestExpireTime = membership.getExpireTime();
            }
        }
        benefit.setVip(!planNames.isEmpty());
        benefit.setPlanNames(planNames);
        benefit.setExpireTime(latestExpireTime);
        return benefit;
    }

    /**
     * 解析用户某个业务维度的每日次数上限，-1 为不限
     */
    public int resolveDailyLimit(Long uid, String biz) {
        VipBizTypeEnum bizType = VipBizTypeEnum.of(biz);
        if (bizType == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_QUOTA_BIZ_ERROR);
        }
        VipBenefit benefit = resolve(uid);
        return switch (bizType) {
            case RAG -> benefit.getRagDailyLimit();
            case CHAT -> benefit.getChatDailyLimit();
        };
    }

    private VipPlanDO queryPlan(String planCode) {
        return vipPlanMapper.selectOne(Wrappers.lambdaQuery(VipPlanDO.class)
                .eq(VipPlanDO::getPlanCode, planCode)
                .eq(VipPlanDO::getDelFlag, 0)
                .last("limit 1"));
    }

    /**
     * 维度合并：-1（不限）优先，否则取较大值
     */
    private int mergeLimit(Integer current, Integer incoming) {
        if (incoming == null) {
            return current == null ? UNLIMITED : current;
        }
        if (current == null) {
            return incoming;
        }
        if (current == UNLIMITED || incoming == UNLIMITED) {
            return UNLIMITED;
        }
        return Math.max(current, incoming);
    }
}