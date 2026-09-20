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
     * 支付场景：{@code QR}（扫码支付）/ {@code PAGE}（网页跳转支付）/ <b>不传 = 渠道默认场景</b>。
     *
     * <p>可选字段，<b>保持向后兼容</b>：老前端不传时行为与以前完全一致（支付宝渠道按 {@code PAGE}
     * 走 {@code alipay.trade.page.pay}），所以这个字段是"加"而不是"改"。
     *
     * <p>目前只有支付宝渠道实现了 {@code QR}：用户<b>不离开我们的页面</b>，后端用
     * {@code alipay.trade.precreate} 预下单拿回一串支付码字符串，前端把它渲染成二维码，
     * 用户用手机上的支付宝沙箱版扫码付款。
     * 不支持该场景的渠道（如 mock）会忽略它、按自己的默认行为下单；渠道不认识的取值会报业务错误
     * （宁可报错也不静默降级，否则"以为要扫码结果被跳走"这类问题很难查）。
     */
    private String payScene;

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