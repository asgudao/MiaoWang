package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 手机号 + 短信验证码登录请求参数
 */
@Data
public class PhoneLoginReqDTO {

    /**
     * 手机号
     */
    private String phone;

    /**
     * 短信验证码
     */
    private String smsCode;
}