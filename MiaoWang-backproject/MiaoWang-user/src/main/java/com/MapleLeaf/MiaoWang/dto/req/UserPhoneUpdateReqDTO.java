package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 换绑手机号
 * <p>
 * 不传 username：改谁由请求头 username/token 还原出的 UserContext 决定。
 * smsCode 是发到**新手机号**上的验证码（scene=change_phone），
 * 只有真正握着这个号的人才能换绑成功。
 */
@Data
public class UserPhoneUpdateReqDTO {

    /**
     * 新手机号
     */
    private String phone;

    /**
     * 新手机号收到的短信验证码
     */
    private String smsCode;

}