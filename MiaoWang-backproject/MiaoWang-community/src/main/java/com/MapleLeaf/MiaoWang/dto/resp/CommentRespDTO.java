package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 评论返回
 * <p>
 * 两级结构：一级评论带 children（它下面的回复，按时间正序）；回复本身不再带 children。
 */
@Data
public class CommentRespDTO {

    private Long commentId;

    private Long postId;

    private Long uid;

    /**
     * 评论人昵称
     */
    private String username;

    private String content;

    private Integer likeCount;

    /**
     * 当前登录用户是否已点赞（未登录恒为 false）
     */
    private Boolean liked;

    private Date createTime;

    /**
     * 父评论 id：0 = 一级评论
     */
    private Long parentId;

    /**
     * 被回复用户 id
     */
    private Long replyUid;

    /**
     * 被回复用户昵称（前端显示"回复 @某某"）
     * <p>
     * 由帖子内已有评论的 uid→username 推导；被回复的评论已删除时会为 null，前端降级显示即可。
     */
    private String replyUsername;

    /**
     * 二级回复列表（只有一级评论才有，回复按时间正序）
     */
    private List<CommentRespDTO> children;
}