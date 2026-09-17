package com.MapleLeaf.MiaoWang.service;

import java.util.Collection;

/**
 * @提及
 * <p>
 * 解析放在 {@link com.MapleLeaf.MiaoWang.support.ContentTagParser} 里（纯函数，好测），
 * 这里只负责"把解析出来的用户名变成对方的一条提及通知"。
 */
public interface MentionService {

    /**
     * 给被提及的人写提及通知（type=6）
     *
     * @param actorUid      操作人（发帖/评论的人）；自己 @ 自己不通知
     * @param actorUsername 操作人昵称（通知里展示"谁 @ 了你"）
     * @param usernames     被提及的用户名（调用方已用 ContentTagParser 解析好）
     * @param postId        所在帖子 id（带着它前端才能跳转）
     * @param targetId      帖内定位：帖子里的提及传 postId，评论里的提及传 commentId
     * @param summary       通知摘要（帖子标题或评论片段）
     */
    void notifyMentions(Long actorUid, String actorUsername, Collection<String> usernames,
                        Long postId, Long targetId, String summary);
}