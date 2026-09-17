package com.MapleLeaf.MiaoWang.common.enums;


import com.MapleLeaf.MiaoWang.common.convention.errorcode.IErrorCode;

public enum UserErrorCodeEnum implements IErrorCode {

    USER_NULL("B000200", "用户记录不存在"),
    USER_NAME_EXIST("B000201", "用户名已存在"),
    USER_EXIST("B000202", "用户记录已存在"),
    USER_SAVE_ERROR("B000203", "用户记录新增失败"),
    USER_NOT_LOGIN("B000204", "用户未登录或登录已过期"),
    USER_PHONE_EXIST("B000205", "手机号已被注册"),
    USER_UPDATE_ONLY_SELF("B000206", "只能修改自己的资料"),

    // ========== A 段：调用方（前端/用户）输入问题 ==========
    CAPTCHA_ERROR("A000700", "图形验证码错误或已过期"),
    SMS_CODE_ERROR("A000701", "短信验证码错误或已过期"),
    SMS_CODE_TOO_MANY_ATTEMPTS("A000702", "验证码错误次数过多，请重新获取"),
    SMS_SEND_TOO_FREQUENT("A000703", "短信发送过于频繁，请稍后再试"),
    SMS_DAILY_LIMIT("A000704", "今日短信发送次数已达上限"),
    PHONE_FORMAT_ERROR("A000705", "手机号格式不正确"),
    SMS_SCENE_ERROR("A000706", "短信场景不合法"),
    PASSWORD_EMPTY("A000707", "密码不能为空"),
    PASSWORD_OLD_ERROR("A000708", "原密码不正确"),
    PASSWORD_SAME("A000709", "新密码不能与原密码相同"),
    PHONE_SAME("A000710", "新手机号与当前手机号相同"),
    ;



    private final String code;

    private final String message;

    UserErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

}
