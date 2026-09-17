package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 修改密码
 * <p>
 * 不传 username：改谁由请求头 username/token 还原出的 UserContext 决定，
 * 前端就算传了别人的用户名也改不到别人（结构上杜绝越权）。
 */
@Data
public class UserPasswordUpdateReqDTO {

    /**
     * 原密码（必须校验，否则拿到 token 就能直接改密码把人踢下线）
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;

}