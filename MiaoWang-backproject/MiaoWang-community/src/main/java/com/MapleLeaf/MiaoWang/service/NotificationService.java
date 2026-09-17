package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.NotificationRespDTO;

/**
 * 通知服务
 */
public interface NotificationService {

    /**
     * 生产一条通知（点赞/评论/回复/关注都走这里）
     * <p>
     * 自己操作自己（自己赞自己的帖子、自己回自己的评论）不会产生通知；
     * 同一人对同一目标的重复触发不会新增记录，而是把原记录刷新到列表顶部并置为未读。
     *
     * @param receiverUid   接收者uid
     * @param type          通知类型
     * @param actorUid      触发者uid
     * @param actorUsername 触发者昵称
     * @param targetId      目标id（帖子id/评论id；关注类传 NOTIFY_TARGET_NONE）
     * @param postId        所属帖子id（关注类传 NOTIFY_TARGET_NONE）
     * @param content       内容摘要，可为空
     */
    void notify(Long receiverUid, Integer type, Long actorUid, String actorUsername,
                Long targetId, Long postId, String content);

    /**
     * 我的通知列表（游标分页，最新的在前）
     *
     * @param lastId     上一页最后一条的 notificationId，首页传 null
     * @param size       每页条数
     * @param type       按类型过滤，null=全部
     * @param unreadOnly 只看未读
     * @return 游标分页结果
     */
    CursorPageRespDTO<NotificationRespDTO> pageMyNotifications(Long lastId, Long size, Integer type, Boolean unreadOnly);

    /**
     * 我的未读通知数
     */
    Long countUnread();

    /**
     * 把某条通知标记为已读（只能操作自己的通知）
     */
    void markRead(Long notificationId);

    /**
     * 我的通知全部标记为已读
     */
    void markAllRead();
}