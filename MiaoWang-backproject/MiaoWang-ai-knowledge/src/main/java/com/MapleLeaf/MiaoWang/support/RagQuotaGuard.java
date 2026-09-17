package com.MapleLeaf.MiaoWang.support;


import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.req.VipQuotaConsumeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.AiErrorCodeEnum;
import com.MapleLeaf.MiaoWang.openfegin.vip.VipQuotaFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RAG 额度闸门
 *
 * <p>顺序很关键：<b>先扣后调</b>。先扣额度再调大模型，扣不到就直接拒绝；
 * 反过来（先回答后扣次数）意味着超额用户照样拿到答案，成本已经产生、次数却扣不回来。
 *
 * <p>两条边界：
 * <ul>
 *   <li><b>匿名不能用</b>：额度是按账号算的，没有 uid 无从计数（也不该给匿名无限用）</li>
 *   <li><b>权益服务挂了就拒服务</b>：和 healthy 同一个口径 —— 校验失败时放行等于开后门，
 *       等做高并发优化时再统一加本地缓存 + 降级</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagQuotaGuard {

    /**
     * 业务标识，与 vip 的 VipBizTypeEnum.RAG 对齐
     */
    private static final String BIZ_RAG = "rag";

    /**
     * vip 里表示"不限次数"的取值（不计数，也就无所谓扣与退）
     */
    private static final int UNLIMITED = -1;

    private final VipQuotaFeign vipQuotaFeign;

    /**
     * 取当前登录用户ID（由 common 的 UserTransmitFilter 依据请求头还原），未登录直接拒绝
     */
    public Long currentUid() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException(AiErrorCodeEnum.AI_NOT_LOGIN);
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            log.warn("登录态里的用户ID非法：{}", userId);
            throw new ClientException(AiErrorCodeEnum.AI_NOT_LOGIN);
        }
    }

    /**
     * 扣一次 RAG 额度，扣不到就抛异常拒绝本次请求
     *
     * @return true = 真的扣了（业务失败时需要回退）；false = 不限次数档位，没计数，不用回退
     */
    public boolean consumeOrThrow(Long uid) {
        VipQuotaFeignRespDTO quota = consumeCall(uid);
        if (!Boolean.TRUE.equals(quota.getAllowed())) {
            // 优先用 vip 的提示（它会区分"今日/本月"），缺失时用本地文案兜底
            String message = StrUtil.isBlank(quota.getMessage())
                    ? AiErrorCodeEnum.AI_QUOTA_EXHAUSTED.message()
                    : quota.getMessage();
            log.info("用户 {} 的 RAG 额度已用完，拒绝本次问答", uid);
            throw new ClientException(message, AiErrorCodeEnum.AI_QUOTA_EXHAUSTED);
        }
        boolean counted = quota.getLimit() == null || quota.getLimit() != UNLIMITED;
        log.info("用户 {} 消耗一次 RAG 额度：本周期已用 {}（上限 {}，周期 {}）", uid, quota.getUsed(),
                quota.getLimit(), StrUtil.blankToDefault(quota.getPeriodDesc(), ""));
        return counted;
    }

    /**
     * 回退一次额度（业务失败时调用）。回退本身失败只记日志，不能盖掉原始业务异常
     */
    public void refund(Long uid) {
        try {
            vipQuotaFeign.refund(VipQuotaConsumeReqDTO.of(uid, BIZ_RAG));
        } catch (Exception e) {
            log.warn("回退 RAG 额度失败，uid={}（额度可能少退一次，需人工核对）", uid, e);
        }
    }

    private VipQuotaFeignRespDTO consumeCall(Long uid) {
        Result<VipQuotaFeignRespDTO> result;
        try {
            result = vipQuotaFeign.consume(VipQuotaConsumeReqDTO.of(uid, BIZ_RAG));
        } catch (Exception e) {
            log.error("调用会员权益服务失败，uid={}", uid, e);
            throw new ServiceException(AiErrorCodeEnum.AI_VIP_SERVICE_ERROR);
        }
        if (result == null || result.getData() == null) {
            log.error("会员权益服务返回体异常，uid={}，result={}", uid, result);
            throw new ServiceException(AiErrorCodeEnum.AI_VIP_SERVICE_ERROR);
        }
        return result.getData();
    }
}