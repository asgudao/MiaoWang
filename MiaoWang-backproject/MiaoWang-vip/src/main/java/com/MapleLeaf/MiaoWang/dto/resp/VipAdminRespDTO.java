package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.util.Date;

/**
 * 后台管理员信息（列表与「当前登录人」共用）
 *
 * <p>刻意不含密码哈希：任何情况下都不该把哈希回传给前端。
 */
@Data
public class VipAdminRespDTO {

    private Long id;

    private String username;

    private String realName;

    private String role;

    private String roleDesc;

    /**
     * 0=禁用 1=启用
     */
    private Integer status;

    private Date lastLoginTime;

    private String lastLoginIp;

    private Date createTime;
}