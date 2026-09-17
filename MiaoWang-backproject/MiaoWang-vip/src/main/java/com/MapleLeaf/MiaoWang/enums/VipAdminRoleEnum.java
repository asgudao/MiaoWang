package com.MapleLeaf.MiaoWang.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 管理员角色
 *
 * <p>只分两级，够用且不会出现"权限矩阵没人能说清"的问题：
 * <ul>
 *   <li>{@link #SUPER}：可以管理管理员账号本身（增删、看操作日志）</li>
 *   <li>{@link #OPERATOR}：只能做业务操作（3D 定制、订单退款、促销码）</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum VipAdminRoleEnum {

    SUPER("SUPER", "超级管理员"),
    OPERATOR("OPERATOR", "运营"),
    ;

    private final String code;

    private final String desc;

    public static VipAdminRoleEnum of(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(trimmed))
                .findFirst()
                .orElse(null);
    }

    public static String descOf(String code) {
        VipAdminRoleEnum item = of(code);
        return item == null ? "未知" : item.desc;
    }
}