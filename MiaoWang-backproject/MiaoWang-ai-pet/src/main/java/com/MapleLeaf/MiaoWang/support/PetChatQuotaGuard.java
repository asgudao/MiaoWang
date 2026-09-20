package com.MapleLeaf.MiaoWang.support;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.req.VipQuotaConsumeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.AiPetErrorCodeEnum;
import com.MapleLeaf.MiaoWang.openfegin.vip.VipQuotaFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * "宠物聊天"额度闸门（biz = {@code chat}）
 *
 * <p>vip 模块里 {@code VipBizTypeEnum.CHAT} 的注释就是「宠物聊天」—— 这个业务位从一开始
 * 就是给本模块留的，所以这里直接用，不新造业务标识。
 *
 * <h3>顺序：先扣后调</h3>
 * 先扣额度再调大模型，扣不到就直接拒绝；反过来（先回答后扣次数）意味着超额用户照样拿到答案，
 * 成本已经产生、次数却扣不回来。
 *
 * <h3>两条边界</h3>
 * <ul>
 *   <li><b>匿名不能用</b>：额度按账号算，没有 uid 无从计数</li>
 *   <li><b>权益服务挂了就拒服务</b>：校验失败时放行等于开后门（与 healthy / ai-knowledge 同口径）</li>
 * </ul>
 *
 * <h3>自动问候不扣次数</h3>
 * 问候是系统主动发起的（用户没开口），扣用户的次数不公平，也没法解释。
 * 它照样记调用日志，成本在日志里看得见 —— 见 {@code AiPetChatService#greet}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetChatQuotaGuard {

    /**
     * 业务标识，与 vip 的 {@code VipBizTypeEnum.CHAT} 对齐
     */
    private static final String BIZ_CHAT = "chat";

    /**
     * vip 里表示"不限次数"的取值（不计数，也就无所谓扣与退）
     */
    private static final int UNLIMITED = -1;

    private final VipQuotaFeign vipQuotaFeign;

    /**
     * 扣一次聊天额度；扣不到直接抛 B000902
     *
     * @return 本次扣减的结果（是否真的计数、还剩几次），供调用方回退与回显
     */
    public QuotaState consumeOrThrow(Long uid) {
        VipQuotaFeignRespDTO quota = consumeCall(uid);
        if (!Boolean.TRUE.equals(quota.getAllowed())) {
            // 优先用 vip 的提示（它会区分"今日/本月"），缺失时用本地文案兜底
            String message = StrUtil.isBlank(quota.getMessage())
                    ? AiPetErrorCodeEnum.PET_CHAT_QUOTA_EXHAUSTED.message()
                    : quota.getMessage();
            log.info("用户 {} 的宠物聊天额度已用完，拒绝本次聊天", uid);
            throw new ClientException(message, AiPetErrorCodeEnum.PET_CHAT_QUOTA_EXHAUSTED);
        }
        boolean counted = quota.getLimit() == null || quota.getLimit() != UNLIMITED;
        log.info("用户 {} 消耗一次宠物聊天额度：本周期已用 {}（上限 {}，周期 {}）", uid, quota.getUsed(),
                quota.getLimit(), StrUtil.blankToDefault(quota.getPeriodDesc(), ""));
        return new QuotaState(counted, quota.getRemaining(), quota.getPeriodDesc());
    }

    /**
     * 回退一次额度（生成失败时调用）。回退本身失败只记日志，不能盖掉原始业务异常。
     */
    public void refund(Long uid) {
        try {
            vipQuotaFeign.refund(VipQuotaConsumeReqDTO.of(uid, BIZ_CHAT));
        } catch (Exception e) {
            log.warn("回退宠物聊天额度失败，uid={}（额度可能少退一次，需人工核对）", uid, e);
        }
    }

    private VipQuotaFeignRespDTO consumeCall(Long uid) {
        Result<VipQuotaFeignRespDTO> result;
        try {
            result = vipQuotaFeign.consume(VipQuotaConsumeReqDTO.of(uid, BIZ_CHAT));
        } catch (Exception e) {
            log.error("调用会员权益服务失败，uid={}", uid, e);
            throw new ServiceException(AiPetErrorCodeEnum.PET_CHAT_VIP_SERVICE_ERROR);
        }
        if (result == null || result.getData() == null) {
            log.error("会员权益服务返回体异常，uid={}，result={}", uid, result);
            throw new ServiceException(AiPetErrorCodeEnum.PET_CHAT_VIP_SERVICE_ERROR);
        }
        return result.getData();
    }

    /**
     * 一次扣减的结果
     *
     * @param counted    是否真的计数了（不限次数的档位不计数，业务失败时也就不需要回退）
     * @param remaining  本周期剩余次数，-1=不限
     * @param periodDesc 周期描述（每日/每月），前端与剩余次数配套显示
     */
    public record QuotaState(boolean counted, Integer remaining, String periodDesc) {
    }
}