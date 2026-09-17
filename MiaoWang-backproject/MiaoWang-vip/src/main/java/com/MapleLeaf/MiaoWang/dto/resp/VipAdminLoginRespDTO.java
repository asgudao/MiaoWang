package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 后台登录响应
 */
@Data
public class VipAdminLoginRespDTO {

    /**
     * 后台 token：后续请求放在请求头 X-Admin-Token 里
     */
    private String token;

    private Long adminId;

    private String username;

    private String realName;

    /**
     * 角色编码：SUPER / OPERATOR
     */
    private String role;

    /**
     * 角色中文名（前端直接展示）
     */
    private String roleDesc;

    /**
     * token 有效期（分钟），前端可据此提前续期提示
     */
    private Integer tokenTtlMinutes;
}