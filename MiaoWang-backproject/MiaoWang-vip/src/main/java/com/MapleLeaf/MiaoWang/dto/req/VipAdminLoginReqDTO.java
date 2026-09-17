package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 后台登录请求
 */
@Data
public class VipAdminLoginReqDTO {

    /**
     * 登录名
     */
    private String username;

    /**
     * 密码（明文传输依赖 HTTPS，本项目内网联调）
     */
    private String password;
}