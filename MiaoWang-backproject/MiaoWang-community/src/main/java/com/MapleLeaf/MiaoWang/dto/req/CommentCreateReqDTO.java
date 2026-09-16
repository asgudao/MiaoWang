package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * 发表评论 / 回复请求
 */
@Data
public class CommentCreateReqDTO {

    /**
     * 所属帖子 id（必填）
     */
    private Long postId;

    /**
     * 评论内容（必填，最长 1000 字）
     */
    private String content;

    /**
     * 父评论 id：不传或 0 = 直接评论帖子；传值 = 回复某条评论
     * <p>
     * 只做两级：如果传的是一条二级评论的 id，会自动挂到它所属的一级评论下。
     */
    private Long parentId;

    /**
     * 被回复用户 id：回复一级评论可以不传（默认该评论作者），回复二级评论时传
     */
    private Long replyUid;
}