package com.MapleLeaf.MiaoWang.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

/**
 * 短信验证码使用场景
 * <p>
 * 为什么要区分场景：注册拿到的验证码不能被用来登录（否则"注册码"就等于"登录万能码"），
 * 所以 Redis key 里带上场景，校验时按场景取，互不通用。
 */
@Getter
public enum SmsSceneEnum {

    /**
     * 手机号登录
     */
    LOGIN("login", "登录"),

    /**
     * 注册（校验手机号真实性）
     */
    REGISTER("register", "注册"),

    /**
     * 换绑手机号（新号必须是本人能收到的号）
     */
    CHANGE_PHONE("change_phone", "换绑手机号"),
    ;

    private final String scene;

    private final String desc;

    SmsSceneEnum(String scene, String desc) {
        this.scene = scene;
        this.desc = desc;
    }

    /**
     * 按场景标识解析，非法值直接拒绝（避免用错 key 前缀）
     */
    public static SmsSceneEnum of(String scene) {
        for (SmsSceneEnum item : values()) {
            if (StrUtil.equals(item.scene, scene)) {
                return item;
            }
        }
        return null;
    }
}