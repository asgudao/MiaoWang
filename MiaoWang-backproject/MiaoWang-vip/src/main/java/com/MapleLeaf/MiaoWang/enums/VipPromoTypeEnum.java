package com.MapleLeaf.MiaoWang.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 促销码类型
 *
 * <p>刻意只用一张表 + 一个 type 字段：两种促销的差别只是"给什么"，
 * 而校验（有效期、名额、每人限一次、状态）完全一样，拆两张表会把同样的校验写两遍。
 */
@Getter
@AllArgsConstructor
public enum VipPromoTypeEnum {

    /**
     * 送会员天数：核销即得，内部会生成一张 0 元已支付订单来发会员（audit 完整，复用同一套发会员逻辑）
     */
    PLAN("PLAN", "赠送会员时长"),

    /**
     * 下单立减：下单时传码抵扣金额，支付成功才真正消耗名额
     * （否则用户用码下单后不付款，码就被白占了）
     */
    DISCOUNT("DISCOUNT", "下单立减"),
    ;

    private final String code;

    private final String desc;

    public static VipPromoTypeEnum of(String code) {
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