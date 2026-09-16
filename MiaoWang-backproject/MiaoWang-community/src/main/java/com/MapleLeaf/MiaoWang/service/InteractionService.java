package com.MapleLeaf.MiaoWang.service;

/**
 * 社区互动：点赞、收藏
 * <p>
 * 涵盖帖子与评论两类目标：点赞/收藏共用一张多态关系表（uid + target_type + target_id），
 * 全部保证幂等：重复点赞/重复收藏不报错、不重复加计数。
 */
public interface InteractionService {

    /**
     * 点赞帖子
     */
    void likePost(Long postId);

    /**
     * 取消点赞帖子
     */
    void unlikePost(Long postId);

    /**
     * 收藏帖子
     */
    void favoritePost(Long postId);

    /**
     * 取消收藏帖子
     */
    void unfavoritePost(Long postId);

    /**
     * 点赞评论
     */
    void likeComment(Long commentId);

    /**
     * 取消点赞评论
     */
    void unlikeComment(Long commentId);
}