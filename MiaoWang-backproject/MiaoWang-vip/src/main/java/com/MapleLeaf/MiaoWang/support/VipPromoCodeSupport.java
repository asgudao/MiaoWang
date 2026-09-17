package com.MapleLeaf.MiaoWang.support;


import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.VipPromoCodeDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPromoRedeemDO;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPromoCodeMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPromoRedeemMapper;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPromoTypeEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Date;

/**
 * 促销码公共校验与核销（订单服务与促销服务共用）
 *
 * <p><b>为什么单独抽一个组件</b>：促销服务需要调用订单服务来发权益，订单服务又需要在
 * 下单/支付时校验促销码 —— 两个 Service 互相注入会形成循环依赖。把"码的读取/校验/核销"
 * 下沉到这里，两个 Service 都只依赖它，依赖方向就是单向的。
 *
 * <p>核销策略：<b>先发权益、后占名额</b>。因为名额是概率性约束（并发下可能超发一次），
 * 而"用户已经付了钱/已经核销"是既成事实 —— 不能因为计数没抢到就把权益收回去，
 * 所以 CAS 失败只记 error 日志（可对账、可追责），不抛异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VipPromoCodeSupport {

    /**
     * 不限名额的标记
     */
    private static final int UNLIMITED = -1;

    private final VipPromoCodeMapper vipPromoCodeMapper;

    private final VipPromoRedeemMapper vipPromoRedeemMapper;

    /**
     * 按码取「可用」的促销码：不存在 / 停用 / 不在有效期 / 名额已满 都会报错
     */
    public VipPromoCodeDO loadUsableCode(String code) {
        if (StrUtil.isBlank(code)) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_NOT_EXIST);
        }
        VipPromoCodeDO promoCode = vipPromoCodeMapper.selectOne(Wrappers.lambdaQuery(VipPromoCodeDO.class)
                // 码统一按大写匹配（存库也统一大写）：用户输入不区分大小写
                .eq(VipPromoCodeDO::getCode, code.trim().toUpperCase(Locale.ROOT))
                .eq(VipPromoCodeDO::getDelFlag, 0)
                .last("limit 1"));
        if (promoCode == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_NOT_EXIST);
        }
        if (promoCode.getStatus() == null || promoCode.getStatus() != 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_NOT_EXIST);
        }
        Date now = new Date();
        if (promoCode.getStartTime() != null && promoCode.getStartTime().after(now)) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_EXPIRED);
        }
        if (promoCode.getEndTime() != null && promoCode.getEndTime().before(now)) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_EXPIRED);
        }
        int maxUseCount = promoCode.getMaxUseCount() == null ? UNLIMITED : promoCode.getMaxUseCount();
        int usedCount = promoCode.getUsedCount() == null ? 0 : promoCode.getUsedCount();
        if (maxUseCount != UNLIMITED && usedCount >= maxUseCount) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_USED_UP);
        }
        return promoCode;
    }

    /**
     * 查某人是否已用过这个码（每人限一次）
     */
    public VipPromoRedeemDO findRedeem(Long promoCodeId, Long uid) {
        return vipPromoRedeemMapper.selectOne(Wrappers.lambdaQuery(VipPromoRedeemDO.class)
                .eq(VipPromoRedeemDO::getPromoCodeId, promoCodeId)
                .eq(VipPromoRedeemDO::getUid, uid)
                .eq(VipPromoRedeemDO::getDelFlag, 0)
                .last("limit 1"));
    }

    /**
     * 核销一个名额并落核销记录（CAS 占用 + 唯一索引兜底）
     *
     * @return true=本次成功占用到名额
     */
    public boolean consume(VipPromoCodeDO promoCode, Long uid, String username, String orderNo) {
        LambdaUpdateWrapper<VipPromoCodeDO> updateWrapper = Wrappers.lambdaUpdate(VipPromoCodeDO.class)
                .eq(VipPromoCodeDO::getId, promoCode.getId())
                .setSql("used_count = used_count + 1");
        int maxUseCount = promoCode.getMaxUseCount() == null ? UNLIMITED : promoCode.getMaxUseCount();
        if (maxUseCount != UNLIMITED) {
            // 有名额上限才加条件：used_count < max_use_count，并发下只有一条能成功
            updateWrapper.lt(VipPromoCodeDO::getUsedCount, maxUseCount);
        }
        int updated = vipPromoCodeMapper.update(null, updateWrapper);
        VipPromoRedeemDO redeemDO = new VipPromoRedeemDO();
        redeemDO.setPromoCodeId(promoCode.getId());
        redeemDO.setCode(promoCode.getCode());
        redeemDO.setUid(uid);
        redeemDO.setUsername(username);
        redeemDO.setOrderNo(orderNo);
        redeemDO.setRedeemTime(new Date());
        redeemDO.setDelFlag(0);
        try {
            vipPromoRedeemMapper.insert(redeemDO);
        } catch (DuplicateKeyException e) {
            // 并发下同一个用户同一码被核销两次：唯一索引挡住了，权益只发一次
            log.error("促销码 {} 用户 {} 重复核销（唯一索引拦下），orderNo={}", promoCode.getCode(), uid, orderNo);
            return false;
        }
        if (updated < 1) {
            log.error("促销码 {} 名额已满但仍发放了权益（并发超发一次），uid={}，orderNo={}",
                    promoCode.getCode(), uid, orderNo);
            return false;
        }
        return true;
    }

    /**
     * 支付成功时核销「立减」名额（订单侧调用）
     *
     * <p>失败不抛异常也不影响发会员：钱已经收了，权益必须给。
     */
    public void consumeDiscountQuietly(Long promoCodeId, String code, Long uid, String username, String orderNo) {
        VipPromoCodeDO promoCode = vipPromoCodeMapper.selectById(promoCodeId);
        if (promoCode == null) {
            log.error("订单 {} 支付成功，但促销码 {} 已不存在，跳过核销", orderNo, code);
            return;
        }
        consume(promoCode, uid, username, orderNo);
    }
}