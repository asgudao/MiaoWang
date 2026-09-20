package com.MapleLeaf.MiaoWang.pay;


import java.math.BigDecimal;

/**
 * 主动查单结果（各渠道查单字段名不同，统一收敛成这个形状）
 *
 * <p>与 {@link PayNotifyResult} 的区别：这是<b>我们主动去问渠道</b>得到的，不是渠道推给我们的，
 * 所以没有渠道回调签名；{@link #tradeStatus} 保留渠道原始状态，便于排查"这笔到底卡在哪一步"。
 *
 * <p>{@link #amount} 是渠道侧的订单金额，<b>必须和订单应付额一致</b>，不一致不允许发货
 * （复用回调那条链路的金额校验，别改这里）。
 *
 * @param channelCode    渠道编码
 * @param orderNo        商户订单号
 * @param channelTradeNo 渠道交易号（对账、排查用；未支付时可能为 null）
 * @param amount         渠道告知的订单金额
 * @param paid           渠道是否判定已支付
 * @param tradeStatus    渠道原始交易状态（如 WAIT_BUYER_PAY / TRADE_SUCCESS / TRADE_CLOSED / TRADE_NOT_EXIST）
 */
public record PayQueryResult(String channelCode,
                             String orderNo,
                             String channelTradeNo,
                             BigDecimal amount,
                             boolean paid,
                             String tradeStatus) {

    /**
     * 收敛成与回调同形状的结果，让「查单补发货」直接复用回调那条链路
     * （金额校验 → CAS 状态流转 → 发会员 → 核销优惠码）
     */
    public PayNotifyResult toNotifyResult() {
        return new PayNotifyResult(channelCode, orderNo, channelTradeNo, amount, paid,
                paid ? null : tradeStatus);
    }
}