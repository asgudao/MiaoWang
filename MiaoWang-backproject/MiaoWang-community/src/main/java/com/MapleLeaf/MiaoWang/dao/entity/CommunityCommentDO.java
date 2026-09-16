package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区评论
 * <p>
 * 本期只建实体与 Mapper（表结构已就绪），评论接口在后续迭代实现：
 * 届时点赞/收藏那套多态设计可以直接复用（target_type 传 2 = 评论）。
 */
@Data
@TableName("miaowang_community_comment")
@EqualsAndHashCode(callSuper = false)
public class CommunityCommentDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long commentId;

    /**
     * 所属帖子 id
     */
    private Long postId;

    /**
     * 评论用户 id
     */
    private Long uid;

    /**
     * 评论用户名（冗余）
     */
    private String username;

    /**
     * 父评论 id，0 为一级评论
     */
    private Long parentId;

    /**
     * 被回复用户 id
     */
    private Long replyUid;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 点赞数（冗余计数）
     */
    private Integer likeCount;

    /**
     * 状态：1待审核 2已发布 3已驳回 4已下架
     */
    private Integer status;
}