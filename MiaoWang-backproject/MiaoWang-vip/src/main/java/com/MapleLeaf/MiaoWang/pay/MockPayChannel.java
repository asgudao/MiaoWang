package com.MapleLeaf.MiaoWang.pay;


import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.config.VipPayProperties;
import com.MapleLeaf.MiaoWang.dao.entity.VipOrderDO;
import com.MapleLeaf.MiaoWang.enums.VipPayChannelEnum;
import com.MapleLeaf.MiaoWang.support.VipPaySignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟支付渠道（联调用）
 *
 * <p>它<b>不是空实现</b>：{@link #buildNotifyParams} 会按真实渠道的样子造一条带签名的回调参数，
 * 联调时 {@code POST /vip/order/{orderNo}/pay} 会把它喂给和真实回调<b>完全相同</b>的处理链路
 * （验签 → 时间戳 → nonce 防重放 → 金额校验 → CAS 状态流转 → 事务内发会员），
 * 所以"换真实渠道"要改的只有这个实现类，状态机与幂等不用重测。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockPayChannel implements PayChannel {

    private final VipPayProperties vipPayProperties;

    @Override
    public String code() {
        return VipPayChannelEnum.MOCK.getCode();
    }

    @Override
    public Map<String, Object> createPayment(VipOrderDO orderDO) {
        Map<String, Object> payParams = new LinkedHashMap<>();
        payParams.put("channel", code());
        payParams.put("orderNo", orderDO.getOrderNo());
        payParams.put("amount", orderDO.getAmount());
        payParams.put("tip", "模拟支付：调用 POST /mapleleaf/miaowang/v1/vip/order/"
                + orderDO.getOrderNo() + "/pay 即视为支付成功（走真实回调链路）");
        return payParams;
    }

    /**
     * 造一条和真实渠道同形状的回调参数（含签名）
     */
    public Map<String, String> buildNotifyParams(VipOrderDO orderDO) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderNo", orderDO.getOrderNo());
        params.put("channelTradeNo", "MOCK" + RandomUtil.randomNumbers(16));
        params.put("amount", orderDO.getAmount() == null ? null : orderDO.getAmount().toPlainString());
        params.put("status", "SUCCESS");
        params.put(VipPaySignUtil.TIMESTAMP_FIELD, String.valueOf(System.currentTimeMillis() / 1000));
        params.put(VipPaySignUtil.NONCE_FIELD, RandomUtil.randomString(24));
        params.put(VipPaySignUtil.SIGN_FIELD, VipPaySignUtil.sign(params, vipPayProperties.getMockSecret()));
        return params;
    }

    @Override
    public PayNotifyResult verifyAndParse(Map<String, String> params) {
        VipPaySignUtil.verify(params, vipPayProperties.getMockSecret(), vipPayProperties.getNotifySkewSeconds());
        boolean success = "SUCCESS".equalsIgnoreCase(StrUtil.trim(params.get("status")));
        return new PayNotifyResult(code(), params.get("orderNo"), params.get("channelTradeNo"),
                parseAmount(params.get("amount")), success, success ? null : params.get("failReason"));
    }

    @Override
    public PayRefundResult refund(VipOrderDO orderDO, String reason) {
        // 模拟退款直接成功；真实渠道这里要调退款接口并处理"渠道侧异步到账"的回调
        String refundNo = "MOCKREFUND" + RandomUtil.randomNumbers(16);
        log.info("【模拟退款】订单 {} 金额 {} 原因 {}，退款单号 {}",
                orderDO.getOrderNo(), orderDO.getAmount(), reason, refundNo);
        return new PayRefundResult(refundNo, true, null);
    }

    @Override
    public String ackBody() {
        return "SUCCESS";
    }

    private BigDecimal parseAmount(String amount) {
        if (StrUtil.isBlank(amount)) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}