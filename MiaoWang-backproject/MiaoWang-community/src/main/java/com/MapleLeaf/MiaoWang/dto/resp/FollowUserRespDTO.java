package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;

/**
 * 关注列表 / 粉丝列表里的一行（一个用户）
 */
@Data
public class FollowUserRespDTO {

    /**
     * 用户id
     */
    private Long uid;

    /**
     * 昵称
     * <p>
     * 由社区侧就近解析（见 UserInfoResolver）：用户服务还没有"按 uid 批量查昵称/头像"的接口，
     * 接口就绪后这个字段会由 Feign 调用填充，头像等字段也会一起补上。
     */
    private String username;

    /**
     * 当前登录用户是否已关注此人（未登录恒为 false）。
     * 粉丝列表用它做"回关"按钮的状态。
     */
    private Boolean followed;

    /**
     * 关系建立时间（关注列表=我关注他的时间；粉丝列表=他关注我的时间）
     */
    private Date createTime;
}