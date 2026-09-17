package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 创建会员订单请求（只下单，不发放会员；支付成功由回调驱动）
 */
@Data
public class VipOrderCreateReqDTO {

    /**
     * 档位编码，如 AI_249
     */
    private String planCode;

    /**
     * 支付渠道：MOCK（默认）/ WECHAT / ALIPAY
     */
    private String payType;

    /**
     * 下单幂等键（前端生成，建议 uuid）。
     *
     * <p>可选但强烈建议传：网络抖动重试、用户连点、弱网重发时，
     * 同一个 requestId 只会产生一单，不会出现"扣两次钱"的经典事故。
     * 不传时退化成「同一档位还有未超时的待支付单就复用」。
     */
    private String requestId;

    /**
     * 优惠码（立减类型）：下单时抵扣金额，支付成功后才真正占用名额。
     *
     * <p>送会员时长的兑换码不走这里，走 {@code POST /vip/promo/redeem}。
     */
    private String promoCode;
}