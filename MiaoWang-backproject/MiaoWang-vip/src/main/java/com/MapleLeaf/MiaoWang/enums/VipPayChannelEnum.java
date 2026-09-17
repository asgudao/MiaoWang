package com.MapleLeaf.MiaoWang.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 支付渠道
 *
 * <p>{@code externalNotify} 是安全开关：只有真实支付渠道才允许从公网回调。
 * {@link #MOCK} 只在联调时通过「已登录 + 校验订单归属」的 {@code /vip/order/{orderNo}/pay} 驱动 ——
 * 如果 mock 也开放外部回调，那么"知道 mock 密钥的人"就能给自己发会员。
 */
@Getter
@AllArgsConstructor
public enum VipPayChannelEnum {

    MOCK("MOCK", "模拟支付", false),
    WECHAT("WECHAT", "微信支付", true),
    ALIPAY("ALIPAY", "支付宝", true),
    ;

    private final String code;

    private final String desc;

    /**
     * 是否允许从公网回调（免登录接口只收这类渠道）
     */
    private final boolean externalNotify;

    /**
     * 解析渠道编码，未知返回 null（由调用方决定报什么错）
     */
    public static VipPayChannelEnum of(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
    }
}