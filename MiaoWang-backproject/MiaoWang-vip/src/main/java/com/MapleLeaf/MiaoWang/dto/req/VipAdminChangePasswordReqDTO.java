package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 修改自己的后台密码
 */
@Data
public class VipAdminChangePasswordReqDTO {

    /**
     * 原密码
     */
    private String oldPassword;

    /**
     * 新密码
     */
    private String newPassword;
}