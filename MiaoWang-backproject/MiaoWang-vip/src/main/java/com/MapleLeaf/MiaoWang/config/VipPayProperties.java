package com.MapleLeaf.MiaoWang.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付相关配置（Nacos: {@code vip.pay.*}，未配置时用下面的默认值，保证开箱能跑）
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vip.pay")
public class VipPayProperties {

    /**
     * 待支付订单的有效分钟数：超时后自动关单，避免一堆挂着不付的单据
     */
    private int pendingMinutes = 30;

    /**
     * 已支付订单可自助退款的窗口天数，超出只能走人工
     */
    private int refundWindowDays = 7;

    /**
     * 回调时间戳允许的前后偏移秒数，超过视为重放
     */
    private int notifySkewSeconds = 300;

    /**
     * mock 渠道的验签密钥。
     *
     * <p><b>仅联调可用</b>：mock 不接受公网回调（见 {@code VipPayChannelEnum.externalNotify}），
     * 所以默认值写在代码里不会成为公网攻击面。真实渠道的密钥必须放 Nacos，
     * 且不能进代码库 —— 这里只留字段位。
     */
    private String mockSecret = "miaowang-mock-pay-secret";

    /**
     * 微信支付密钥（真实渠道接入时配置；未配置则 WECHAT 渠道不可用）
     */
    private String wechatSecret;

    /**
     * 支付宝密钥（<b>历史字段</b>：真实支付宝接入走下面的 {@link #alipay} 分组配置，
     * 这里保留只是为了不打挂历史上已经写进 Nacos 的 {@code vip.pay.alipay-secret}，新代码不要用）
     */
    private String alipaySecret;

    /**
     * 支付宝渠道配置（Nacos: {@code vip.pay.alipay.*}）
     *
     * <p><b>密钥一律不写进代码库</b>：这里只留字段位与沙箱默认值，真实密钥放 Nacos。
     * 关键项没配时渠道 <b>fail-closed</b>（报 {@code B000533 支付宝渠道未配置}），
     * 绝不静默降级到 mock —— 那等于用户不花钱就能拿到会员。
     */
    private Alipay alipay = new Alipay();

    @Getter
    @Setter
    public static class Alipay {

        /**
         * 沙箱应用的 APPID（支付宝开放平台沙箱控制台里那个，不是真实商户 APPID）
         */
        private String appId;

        /**
         * 应用私钥（PKCS8，控制台「应用私钥」那一串；PEM 头尾与换行会被自动清理）
         */
        private String merchantPrivateKey;

        /**
         * 支付宝公钥（控制台「支付宝公钥」那一串，<b>不是应用公钥</b>；用它验回调签名）
         */
        private String alipayPublicKey;

        /**
         * 网关地址：默认沙箱网关；上生产要换成 {@code https://openapi.alipay.com/gateway.do}
         */
        private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

        /**
         * 异步通知地址：<b>必须是渠道服务器能访问到的公网地址</b>。
         *
         * <p>默认留空 —— 开发机在局域网内，支付宝的服务器根本打不进来，配一个假的 localhost
         * 只会让人误以为回调通了。留空时下单不会带 notify_url，支付结果靠
         * {@code return_url} 跳回 + {@code POST /vip/order/{orderNo}/sync} 主动查单对账。
         */
        private String notifyUrl;

        /**
         * 同步跳转地址：支付完成后<b>用户浏览器</b>会被跳到这里。
         *
         * <p>走的是浏览器而不是支付宝服务器，所以默认的 localhost 在开发机上真的能用；
         * Web 端页面路径约定为 {@code /vip/pay/return}。
         */
        private String returnUrl = "http://localhost:5173/vip/pay/return";

        /**
         * 签名算法：RSA2（沙箱与生产都用它）
         */
        private String signType = "RSA2";

        /**
         * 收款商户 PID（可空）
         *
         * <p><b>当前实现用不到它</b>：PC 网页支付只认 APPID，收款方由 APPID 决定；
         * 这个字段是给以后可能接的接口（如 {@code alipay.trade.create}）预留的，
         * 留着是为了让配置形状和支付宝控制台对得上，别误以为配了它才会收款到某个账号。
         */
        private String sellerId;
    }
}