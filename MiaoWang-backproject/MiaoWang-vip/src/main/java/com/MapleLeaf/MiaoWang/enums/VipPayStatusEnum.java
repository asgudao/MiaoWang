package com.MapleLeaf.MiaoWang.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会员订单支付状态（订单状态机）
 *
 * <pre>
 *   待支付 ──支付回调──▶ 已支付 ──退款──▶ 已退款
 *      │
 *      └──超时/用户放弃──▶ 已关闭（不可逆）
 * </pre>
 *
 * <p>只有 {@link #PENDING} 能被回调改成 {@link #PAID}，只有 {@link #PAID} 能退款：
 * 每次流转都是带 old 状态条件的 CAS 更新，所以并发回调/重复点退款不会重复发会员、也不会重复回收时长。
 */
@Getter
@AllArgsConstructor
public enum VipPayStatusEnum {

    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CLOSED(2, "已关闭"),
    REFUNDED(3, "已退款"),
    ;

    private final int code;

    private final String desc;

    public static VipPayStatusEnum of(Integer code) {
        if (code != null) {
            for (VipPayStatusEnum item : values()) {
                if (item.code == code) {
                    return item;
                }
            }
        }
        return null;
    }

    public static String descOf(Integer code) {
        VipPayStatusEnum item = of(code);
        return item == null ? "未知" : item.desc;
    }
}