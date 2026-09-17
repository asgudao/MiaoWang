package com.MapleLeaf.MiaoWang.support;


import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.VipBenefitFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.PetErrorCodeEnum;
import com.MapleLeaf.MiaoWang.openfegin.vip.VipServiceFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 宠物模块的会员权益入口
 *
 * <p>「能养几只」不是宠物模块说了算，而是会员档位说了算（免费每种物种 1 只、会员每种 5 只…）。
 * 规则统一由 vip 模块判定，这里只消费结果：改价格、改额度只需要改 vip 的档位表，本模块一行都不用动。
 *
 * <p>权益服务不可用时<b>报错不放行</b>（与 healthy 相同口径）：
 * 校验失败还放行等于把付费权益白送。等做高并发优化时再统一加本地缓存 + 降级。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetBenefitResolver {

    /**
     * vip 里代表「不限」的取值
     */
    private static final int UNLIMITED = -1;

    private final VipServiceFeign vipServiceFeign;

    /**
     * 查某用户可养宠物的<b>总数量上限</b>（不区分物种）
     *
     * <p>口径变更说明：字段名 {@code petLimitPerType} 是历史遗留（早期按物种各算一份），
     * 现在语义是<b>宠物总数上限</b> —— 免费档 2 只、会员档 5 只，不再按物种分开计数。
     * 字段名与库列名暂不改（改名会动到对外 JSON 契约），改动时前后端同步。
     *
     * @param uid 用户ID
     * @return 上限，-1 表示不限
     */
    public int petLimitTotal(Long uid) {
        VipBenefitFeignRespDTO benefit = benefitOf(uid);
        Integer limit = benefit.getPetLimitPerType();
        if (limit == null) {
            // 配置缺字段时按「不限」处理：宁可放宽也不要因为脏配置把用户挡在门外
            log.warn("会员权益里没有宠物数量上限（petLimitPerType），uid={}，按不限处理", uid);
            return UNLIMITED;
        }
        return limit;
    }

    /**
     * 是否不限数量
     */
    public boolean isUnlimited(int limit) {
        return limit == UNLIMITED;
    }

    private VipBenefitFeignRespDTO benefitOf(Long uid) {
        Result<VipBenefitFeignRespDTO> result;
        try {
            result = vipServiceFeign.getBenefitByUid(uid);
        } catch (Exception e) {
            log.error("调用会员权益服务失败，uid={}", uid, e);
            throw new ServiceException(PetErrorCodeEnum.PET_VIP_SERVICE_ERROR);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.error("会员权益服务返回异常结果，uid={}，result={}", uid, result);
            throw new ServiceException(PetErrorCodeEnum.PET_VIP_SERVICE_ERROR);
        }
        return result.getData();
    }
}