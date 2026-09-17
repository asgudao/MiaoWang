package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.NotificationRespDTO;
import com.MapleLeaf.MiaoWang.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区通知接口（谁赞了你 / 谁评论了你 / 谁回复了你 / 谁关注了你）
 */
@RestController
@RequiredArgsConstructor
public class CommunityNotificationController {

    private final NotificationService notificationService;

    /**
     * 我的通知列表（游标分页，最新在前）
     *
     * @param type       按类型过滤：1点赞帖子 2点赞评论 3评论帖子 4回复评论 5关注；不传=全部
     * @param unreadOnly 只看未读
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/notification")
    public Result<CursorPageRespDTO<NotificationRespDTO>> pageMyNotifications(@RequestParam(value = "lastId", required = false) Long lastId,
                                                                             @RequestParam(value = "size", required = false) Long size,
                                                                             @RequestParam(value = "type", required = false) Integer type,
                                                                             @RequestParam(value = "unreadOnly", required = false) Boolean unreadOnly) {
        return Results.success(notificationService.pageMyNotifications(lastId, size, type, unreadOnly));
    }

    /**
     * 我的未读通知数（前端小红点）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/notification/unread-count")
    public Result<Long> countUnread() {
        return Results.success(notificationService.countUnread());
    }

    /**
     * 单条标记已读
     */
    @PutMapping("/mapleleaf/miaowang/v1/community/notification/{notificationId}/read")
    public Result<Void> markRead(@PathVariable("notificationId") Long notificationId) {
        notificationService.markRead(notificationId);
        return Results.success();
    }

    /**
     * 全部标记已读
     */
    @PutMapping("/mapleleaf/miaowang/v1/community/notification/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead();
        return Results.success();
    }
}