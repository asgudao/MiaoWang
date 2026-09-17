package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 发送短信验证码请求参数
 */
@Data
public class SmsSendReqDTO {

    /**
     * 手机号
     */
    private String phone;

    /**
     * 场景：login / register（见 SmsSceneEnum）
     */
    private String scene;

    /**
     * 图形验证码标识
     */
    private String captchaKey;

    /**
     * 图形验证码
     */
    private String captchaCode;
}