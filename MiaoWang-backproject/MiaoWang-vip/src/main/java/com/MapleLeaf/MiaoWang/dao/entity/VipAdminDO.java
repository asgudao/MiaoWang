package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 后台管理员
 *
 * <p><b>为什么不复用 miaowang_user 加一个 role 字段</b>：
 * 后台账号与 C 端账号是两套身份体系（生命周期、权限、审计要求都不同），
 * 混在一张表里意味着"任何一个用户表漏洞都可能变成后台入口"，而且会牵动 user 模块。
 * 独立一张表 + 独立 token，越权面天然更小。
 *
 * <p>密码用 BCrypt 存哈希（C 端那边目前是明文，属已知技术债），
 * 登录失败次数限制与锁定暂未做（挂账）。
 */
@Data
@TableName("miaowang_vip_admin")
@EqualsAndHashCode(callSuper = false)
public class VipAdminDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录名
     */
    private String username;

    /**
     * 密码哈希（BCrypt）
     */
    private String passwordHash;

    /**
     * 姓名（审计展示）
     */
    private String realName;

    /**
     * 角色：SUPER / OPERATOR
     */
    private String role;

    /**
     * 状态：0=禁用 1=启用
     */
    private Integer status;

    /**
     * 最近登录时间
     */
    private Date lastLoginTime;

    /**
     * 最近登录IP
     */
    private String lastLoginIp;
}