package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 会员订单退款请求
 */
@Data
public class VipRefundReqDTO {

    /**
     * 退款原因（可选，会随订单落库便于客服核对）
     */
    private String reason;
}