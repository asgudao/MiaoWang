package com.MapleLeaf.MiaoWang.support;


import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.UserVipDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPlanDO;
import com.MapleLeaf.MiaoWang.dao.mapper.UserVipMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPlanMapper;
import com.MapleLeaf.MiaoWang.enums.VipBizTypeEnum;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPlanCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipQuotaPeriodEnum;
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
 *   <li>各维度独立取最大值 —— 宠物数量、RAG、聊天、健康档案、提醒互不影响</li>
 *   <li>{@link #UNLIMITED}（-1）优先级最高：任一档位不限，该维度就不限</li>
 *   <li>0/1 开关型权益（导出、AI 解读）取「或」：任一档位给了权限就拥有</li>
 *   <li>额度是「上限 + 周期」的组合，按<b>折算月总量</b>比大小（DAY 的月总量 = 上限×30）：
 *       比如 5 次/月 与 10 次/天 并存时，取后者（月总量 300 &gt; 5）</li>
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

    /**
     * FREE 档配置缺失时的代码兜底值（正常情况下不会走到，只保证服务不因配置缺失而不可用）
     *
     * <p>口径与免费档档位数据保持一致：宠物**总数**上限 2（不区分物种）、RAG 5/月、聊天 5/天、健康记录 20、提醒 3。
     */
    private static final int FALLBACK_PET_LIMIT_PER_TYPE = 2;

    private static final int FALLBACK_RAG_LIMIT = 5;

    private static final String FALLBACK_RAG_PERIOD = VipQuotaPeriodEnum.MONTH.getPeriod();

    private static final int FALLBACK_CHAT_LIMIT = 5;

    private static final String FALLBACK_CHAT_PERIOD = VipQuotaPeriodEnum.DAY.getPeriod();

    private static final int FALLBACK_HEALTH_RECORD_LIMIT = 20;

    private static final int FALLBACK_REMINDER_LIMIT = 3;

    /**
     * 一天的折算系数：用于把 DAY 额度换算成月总量比较宽松程度
     */
    private static final long DAYS_PER_MONTH = 30L;

    private final VipPlanMapper vipPlanMapper;

    private final UserVipMapper userVipMapper;

    /**
     * 额度配置：上限 + 周期
     */
    public record QuotaSetting(int limit, String period) {

        public static QuotaSetting unlimited() {
            return new QuotaSetting(UNLIMITED, VipQuotaPeriodEnum.DAY.getPeriod());
        }
    }

    /**
     * 解析用户当前权益（uid 为空时返回免费档权益）
     */
    public VipBenefit resolve(Long uid) {
        VipPlanDO freePlan = queryPlan(VipPlanCodeEnum.FREE.getCode());
        if (freePlan == null) {
            log.warn("会员档位表中缺少 FREE 档配置，已使用代码兜底值（宠物各1只 / RAG 5次每月 / 聊天 5轮每日 / 健康记录20条 / 提醒3条）");
        }
        VipBenefit benefit = new VipBenefit();
        benefit.setVip(false);
        benefit.setPlanNames(Collections.emptyList());
        benefit.setExpireTime(null);
        // 免费档作为合并起点：付费档权益只会更高，所以从免费档起合并既安全又能兜住配错的档位
        benefit.setPetLimitPerType(valueOr(freePlan == null ? null : freePlan.getPetLimitPerType(), FALLBACK_PET_LIMIT_PER_TYPE));
        benefit.setHealthRecordLimit(valueOr(freePlan == null ? null : freePlan.getHealthRecordLimit(), FALLBACK_HEALTH_RECORD_LIMIT));
        benefit.setHealthExport(valueOr(freePlan == null ? null : freePlan.getHealthExport(), 0));
        benefit.setHealthAiAnalysis(valueOr(freePlan == null ? null : freePlan.getHealthAiAnalysis(), 0));
        benefit.setReminderLimit(valueOr(freePlan == null ? null : freePlan.getReminderLimit(), FALLBACK_REMINDER_LIMIT));

        QuotaSetting ragQuota = quotaOf(freePlan, VipBizTypeEnum.RAG);
        QuotaSetting chatQuota = quotaOf(freePlan, VipBizTypeEnum.CHAT);
        if (uid == null) {
            applyQuota(benefit, VipBizTypeEnum.RAG, ragQuota);
            applyQuota(benefit, VipBizTypeEnum.CHAT, chatQuota);
            return benefit;
        }
        // 生效中 = 状态正常 + 未删除 + 未过期
        List<UserVipDO> memberships = userVipMapper.selectList(Wrappers.lambdaQuery(UserVipDO.class)
                .eq(UserVipDO::getUid, uid)
                .eq(UserVipDO::getStatus, 1)
                .eq(UserVipDO::getDelFlag, 0)
                .gt(UserVipDO::getExpireTime, new Date()));
        if (memberships != null && !memberships.isEmpty()) {
            List<String> planNames = new ArrayList<>();
            Date latestExpireTime = null;
            for (UserVipDO membership : memberships) {
                VipPlanDO plan = queryPlan(membership.getPlanCode());
                if (plan == null) {
                    log.warn("用户 {} 持有档位 {} 但档位配置已不存在，该档位权益被忽略", uid, membership.getPlanCode());
                    continue;
                }
                benefit.setPetLimitPerType(mergeLimit(benefit.getPetLimitPerType(), plan.getPetLimitPerType()));
                benefit.setHealthRecordLimit(mergeLimit(benefit.getHealthRecordLimit(), plan.getHealthRecordLimit()));
                benefit.setReminderLimit(mergeLimit(benefit.getReminderLimit(), plan.getReminderLimit()));
                benefit.setHealthExport(mergeFlag(benefit.getHealthExport(), plan.getHealthExport()));
                benefit.setHealthAiAnalysis(mergeFlag(benefit.getHealthAiAnalysis(), plan.getHealthAiAnalysis()));
                ragQuota = mergeQuota(ragQuota, quotaOf(plan, VipBizTypeEnum.RAG));
                chatQuota = mergeQuota(chatQuota, quotaOf(plan, VipBizTypeEnum.CHAT));
                planNames.add(plan.getPlanName());
                if (latestExpireTime == null || membership.getExpireTime().after(latestExpireTime)) {
                    latestExpireTime = membership.getExpireTime();
                }
            }
            benefit.setVip(!planNames.isEmpty());
            benefit.setPlanNames(planNames);
            benefit.setExpireTime(latestExpireTime);
        }
        applyQuota(benefit, VipBizTypeEnum.RAG, ragQuota);
        applyQuota(benefit, VipBizTypeEnum.CHAT, chatQuota);
        return benefit;
    }

    /**
     * 解析用户某业务的额度配置（上限 + 周期），供额度服务计数使用
     */
    public QuotaSetting resolveQuota(Long uid, String biz) {
        VipBizTypeEnum bizType = requireBiz(biz);
        VipBenefit benefit = resolve(uid);
        return bizType == VipBizTypeEnum.RAG
                ? new QuotaSetting(valueOr(benefit.getRagLimit(), UNLIMITED), benefit.getRagPeriod())
                : new QuotaSetting(valueOr(benefit.getChatLimit(), UNLIMITED), benefit.getChatPeriod());
    }

    private VipBizTypeEnum requireBiz(String biz) {
        VipBizTypeEnum bizType = VipBizTypeEnum.of(biz);
        if (bizType == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_QUOTA_BIZ_ERROR);
        }
        return bizType;
    }

    /**
     * 取档位上某业务的额度配置，配置缺失/周期非法时兜底，保证额度服务不被脏数据拖死
     */
    private QuotaSetting quotaOf(VipPlanDO plan, VipBizTypeEnum bizType) {
        if (plan == null) {
            return bizType == VipBizTypeEnum.RAG
                    ? new QuotaSetting(FALLBACK_RAG_LIMIT, FALLBACK_RAG_PERIOD)
                    : new QuotaSetting(FALLBACK_CHAT_LIMIT, FALLBACK_CHAT_PERIOD);
        }
        Integer limit = bizType == VipBizTypeEnum.RAG ? plan.getRagLimit() : plan.getChatLimit();
        String period = bizType == VipBizTypeEnum.RAG ? plan.getRagPeriod() : plan.getChatPeriod();
        VipQuotaPeriodEnum periodEnum = VipQuotaPeriodEnum.of(period);
        if (periodEnum == null) {
            log.warn("档位 {} 的 {} 额度周期配置非法（{}），已按 DAY 处理", plan.getPlanCode(), bizType.getDesc(), period);
            periodEnum = VipQuotaPeriodEnum.DAY;
        }
        if (limit == null) {
            log.warn("档位 {} 的 {} 额度上限为空，已按 0 处理", plan.getPlanCode(), bizType.getDesc());
            limit = 0;
        }
        return new QuotaSetting(limit, periodEnum.getPeriod());
    }

    /**
     * 合并额度：-1（不限）最大，否则折合月总量比大小；持平时优先 DAY（重置更快、体验更好）
     */
    private QuotaSetting mergeQuota(QuotaSetting current, QuotaSetting incoming) {
        if (current == null) {
            return incoming;
        }
        if (incoming == null) {
            return current;
        }
        if (current.limit() == UNLIMITED || incoming.limit() == UNLIMITED) {
            return QuotaSetting.unlimited();
        }
        long currentMonthly = monthlyAmount(current);
        long incomingMonthly = monthlyAmount(incoming);
        if (incomingMonthly > currentMonthly) {
            return incoming;
        }
        if (incomingMonthly < currentMonthly) {
            return current;
        }
        return VipQuotaPeriodEnum.DAY.getPeriod().equals(current.period()) ? current : incoming;
    }

    private long monthlyAmount(QuotaSetting setting) {
        return VipQuotaPeriodEnum.DAY.getPeriod().equals(setting.period())
                ? setting.limit() * DAYS_PER_MONTH
                : setting.limit();
    }

    private void applyQuota(VipBenefit benefit, VipBizTypeEnum bizType, QuotaSetting setting) {
        if (bizType == VipBizTypeEnum.RAG) {
            benefit.setRagLimit(setting.limit());
            benefit.setRagPeriod(setting.period());
        } else {
            benefit.setChatLimit(setting.limit());
            benefit.setChatPeriod(setting.period());
        }
    }

    /**
     * 数量类权益合并：-1（不限）优先，否则取较大值
     */
    private Integer mergeLimit(Integer current, Integer incoming) {
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

    /**
     * 开关类权益合并：0/1 取大（任一档位给了权限就拥有）
     */
    private Integer mergeFlag(Integer current, Integer incoming) {
        if (incoming == null) {
            return current == null ? 0 : current;
        }
        if (current == null) {
            return incoming;
        }
        return Math.max(current, incoming);
    }

    private VipPlanDO queryPlan(String planCode) {
        return vipPlanMapper.selectOne(Wrappers.lambdaQuery(VipPlanDO.class)
                .eq(VipPlanDO::getPlanCode, planCode)
                .eq(VipPlanDO::getDelFlag, 0)
                .last("limit 1"));
    }

    private int valueOr(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}