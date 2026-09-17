package com.MapleLeaf.MiaoWang.pay;


/**
 * 退款结果
 *
 * @param refundNo   退款单号（落库，便于和渠道对账）
 * @param success    是否退款成功
 * @param failReason 失败原因（success=false 时有值）
 */
public record PayRefundResult(String refundNo, boolean success, String failReason) {
}