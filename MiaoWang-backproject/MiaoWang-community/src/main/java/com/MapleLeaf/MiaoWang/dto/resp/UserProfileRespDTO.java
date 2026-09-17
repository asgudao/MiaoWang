package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 个人主页的头部信息（谁的帖子、多少粉丝、我有没有关注他）
 * <p>
 * 帖子流、点赞流、宠物列表分别走各自的接口，这个对象只负责"头部那一块"。
 */
@Data
public class UserProfileRespDTO {

    /**
     * 用户id
     */
    private Long uid;

    /**
     * 昵称（社区侧就近解析，可能为 null；用户服务补上按 uid 查询后会稳定返回）
     */
    private String username;

    /**
     * 是否本人（决定前端显示"关注"还是"编辑资料"）
     */
    private Boolean self;

    /**
     * 当前登录用户是否已关注此人（未登录恒为 false；看自己主页恒为 false）
     */
    private Boolean followed;

    /**
     * 他发布过的帖子数
     */
    private Long postCount;

    /**
     * 他所有帖子收到的赞总数
     */
    private Long likeReceivedCount;

    /**
     * 他关注了多少人
     */
    private Long followingCount;

    /**
     * 他有多少粉丝
     */
    private Long followerCount;
}