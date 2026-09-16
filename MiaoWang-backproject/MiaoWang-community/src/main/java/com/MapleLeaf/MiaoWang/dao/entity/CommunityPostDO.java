package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区帖子
 */
@Data
@TableName("miaowang_community_post")
@EqualsAndHashCode(callSuper = false)
public class CommunityPostDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long postId;

    /**
     * 所属版块 id
     */
    private Long sectionId;

    /**
     * 发帖用户 id（关联 miaowang_user.uid）
     */
    private Long uid;

    /**
     * 发帖用户名（冗余，列表展示免联表）
     */
    private String username;

    /**
     * 标题（宠物美照等版块可为空）
     */
    private String title;

    /**
     * 正文内容
     */
    private String content;

    /**
     * 封面图 URL（取 sort=0 的图片，冗余便于列表展示）
     */
    private String coverUrl;

    /**
     * 状态：0草稿 1待审核 2已发布 3已驳回 4已下架
     */
    private Integer status;

    /**
     * 是否置顶：0否 1是
     */
    private Integer isTop;

    /**
     * 浏览数
     */
    private Integer viewCount;

    /**
     * 点赞数（冗余计数，列表页不用去 COUNT 点赞大表）
     */
    private Integer likeCount;

    /**
     * 收藏数（冗余计数）
     */
    private Integer favoriteCount;

    /**
     * 评论数（冗余计数，评论功能后期接入）
     */
    private Integer commentCount;
}