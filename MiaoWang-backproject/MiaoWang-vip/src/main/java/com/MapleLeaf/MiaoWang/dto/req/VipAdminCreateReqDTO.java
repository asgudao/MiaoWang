package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 新增后台管理员请求（仅超管可用）
 */
@Data
public class VipAdminCreateReqDTO {

    /**
     * 登录名
     */
    private String username;

    /**
     * 初始密码（明文传入，落库前 BCrypt 哈希）
     */
    private String password;

    /**
     * 姓名
     */
    private String realName;

    /**
     * 角色：SUPER / OPERATOR
     */
    private String role;
}