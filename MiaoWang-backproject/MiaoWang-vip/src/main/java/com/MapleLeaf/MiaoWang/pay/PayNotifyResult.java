package com.MapleLeaf.MiaoWang.pay;


import java.math.BigDecimal;

/**
 * 支付回调解析结果（各渠道回调字段名不同，统一收敛成这个形状）
 *
 * @param channelCode    渠道编码
 * @param orderNo        商户订单号
 * @param channelTradeNo 渠道交易号（对账、排查用）
 * @param amount         渠道告知的支付金额（必须和订单金额一致，否则拒绝）
 * @param success        渠道是否判定支付成功
 * @param failReason     失败原因（success=false 时有值）
 */
public record PayNotifyResult(String channelCode,
                              String orderNo,
                              String channelTradeNo,
                              BigDecimal amount,
                              boolean success,
                              String failReason) {
}