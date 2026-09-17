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
     * 支付宝密钥（同上）
     */
    private String alipaySecret;
}