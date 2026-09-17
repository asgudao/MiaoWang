package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;

/**
 * 一条通知
 */
@Data
public class NotificationRespDTO {

    /**
     * 通知id（同时作为游标分页的游标）
     */
    private Long notificationId;

    /**
     * 通知类型：1点赞帖子 2点赞评论 3评论帖子 4回复评论 5关注
     */
    private Integer type;

    /**
     * 触发者uid
     */
    private Long actorUid;

    /**
     * 触发者昵称（触发时的快照）
     */
    private String actorUsername;

    /**
     * 目标id：帖子id / 评论id；关注类通知为 0
     */
    private Long targetId;

    /**
     * 所属帖子id，前端点击跳转用；关注类通知为 0
     */
    private Long postId;

    /**
     * 内容摘要（评论内容 / 帖子标题 / 被点赞的评论内容）
     */
    private String content;

    /**
     * 0未读 1已读
     */
    private Integer readFlag;

    /**
     * 通知时间
     */
    private Date createTime;
}