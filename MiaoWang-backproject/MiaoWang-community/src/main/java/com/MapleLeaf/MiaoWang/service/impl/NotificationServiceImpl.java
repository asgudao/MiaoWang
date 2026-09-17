package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityNotificationDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityNotificationMapper;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.NotificationRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.BlockService;
import com.MapleLeaf.MiaoWang.service.NotificationService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 社区通知实现
 * <p>
 * 通知的写入是"顺手做"的：点赞、评论、关注成功后各调一次 notify，
 * 失败不抛给调用方（通知发不出去不该让点赞失败），只记日志。
 * <p>
 * 列表用游标分页（notification_id 倒序）：通知是持续插入的流水，
 * 用 offset 翻页时新通知会不断把旧数据往后挤，翻到第二页就会看到第一页的内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<CommunityNotificationMapper, CommunityNotificationDO> implements NotificationService {

    private final BlockService blockService;

    @Override
    public void notify(Long receiverUid, Integer type, Long actorUid, String actorUsername,
                       Long targetId, Long postId, String content) {
        // 自己操作自己不通知（自己赞自己、自己回自己）
        if (receiverUid == null || actorUid == null || type == null || receiverUid.equals(actorUid)) {
            return;
        }
        // 双方有拉黑关系就不写通知：拉黑之后不该再收到对方的消息（历史通知在拉黑那一刻已经清掉了）
        if (blockService.blockedBetween(receiverUid, actorUid)) {
            log.debug("双方存在拉黑关系，通知不写入, receiverUid={}, actorUid={}", receiverUid, actorUid);
            return;
        }
        CommunityNotificationDO notificationDO = new CommunityNotificationDO();
        notificationDO.setReceiverUid(receiverUid);
        notificationDO.setType(type);
        notificationDO.setActorUid(actorUid);
        notificationDO.setActorUsername(actorUsername);
        // 关注类通知没有目标对象，统一存 0（存 NULL 会让唯一键去重失效）
        notificationDO.setTargetId(targetId == null ? CommunityConstant.NOTIFY_TARGET_NONE : targetId);
        notificationDO.setPostId(postId == null ? CommunityConstant.NOTIFY_TARGET_NONE : postId);
        notificationDO.setContent(truncate(content));
        notificationDO.setReadFlag(CommunityConstant.NOTIFY_UNREAD);
        try {
            baseMapper.insert(notificationDO);
            return;
        } catch (DuplicateKeyException e) {
            // 同一个人对同一个目标的重复触发：取消赞再赞、取关再关注
            log.debug("通知重复触发，改为刷新, receiverUid={}, type={}, targetId={}, actorUid={}",
                    receiverUid, type, targetId, actorUid);
        }
        // 删旧行 + 插新行，而不是 UPDATE 旧行：
        // notification_id 是自增的，UPDATE 后 id 不变，这条通知会永远卡在它第一次出现的位置；
        // 删掉重插能拿到新的 id，列表按 id 倒序，它就会重新跑到顶部并回到未读状态。
        try {
            baseMapper.delete(Wrappers.lambdaQuery(CommunityNotificationDO.class)
                    .eq(CommunityNotificationDO::getReceiverUid, receiverUid)
                    .eq(CommunityNotificationDO::getType, type)
                    .eq(CommunityNotificationDO::getTargetId, notificationDO.getTargetId())
                    .eq(CommunityNotificationDO::getActorUid, actorUid));
            notificationDO.setNotificationId(null);
            baseMapper.insert(notificationDO);
        } catch (DuplicateKeyException e) {
            // 并发下另一个线程刚插进去了一条同样的通知，合并掉即可
            log.debug("通知刷新时并发冲突，已忽略, receiverUid={}, type={}", receiverUid, type);
        } catch (Exception e) {
            // 通知失败不能影响主流程（点赞/评论/关注已经成功了）
            log.warn("通知写入失败, receiverUid={}, type={}, actorUid={}", receiverUid, type, actorUid, e);
        }
    }

    @Override
    public CursorPageRespDTO<NotificationRespDTO> pageMyNotifications(Long lastId, Long size, Integer type, Boolean unreadOnly) {
        Long uid = currentLoginUid();
        long limit = normalizeSize(size);
        boolean onlyUnread = Boolean.TRUE.equals(unreadOnly);
        List<CommunityNotificationDO> rows = baseMapper.selectList(Wrappers.lambdaQuery(CommunityNotificationDO.class)
                .eq(CommunityNotificationDO::getReceiverUid, uid)
                .eq(type != null, CommunityNotificationDO::getType, type)
                .eq(onlyUnread, CommunityNotificationDO::getReadFlag, CommunityConstant.NOTIFY_UNREAD)
                .lt(lastId != null, CommunityNotificationDO::getNotificationId, lastId)
                .orderByDesc(CommunityNotificationDO::getNotificationId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<CommunityNotificationDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        if (CollUtil.isEmpty(pageList)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        List<NotificationRespDTO> records = pageList.stream()
                .map(notificationDO -> BeanUtil.toBean(notificationDO, NotificationRespDTO.class))
                .collect(Collectors.toList());
        Long nextCursor = hasMore ? records.get(records.size() - 1).getNotificationId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    @Override
    public Long countUnread() {
        Long uid = currentLoginUid();
        return baseMapper.selectCount(Wrappers.lambdaQuery(CommunityNotificationDO.class)
                .eq(CommunityNotificationDO::getReceiverUid, uid)
                .eq(CommunityNotificationDO::getReadFlag, CommunityConstant.NOTIFY_UNREAD));
    }

    @Override
    public void markRead(Long notificationId) {
        Long uid = currentLoginUid();
        // 归属校验直接写进 WHERE：改不到别人的通知，也不用先查一次
        int updated = baseMapper.update(null, Wrappers.lambdaUpdate(CommunityNotificationDO.class)
                .set(CommunityNotificationDO::getReadFlag, CommunityConstant.NOTIFY_READ)
                .eq(CommunityNotificationDO::getNotificationId, notificationId)
                .eq(CommunityNotificationDO::getReceiverUid, uid)
                .eq(CommunityNotificationDO::getReadFlag, CommunityConstant.NOTIFY_UNREAD));
        if (updated == 0) {
            // 已读的重复点击不该报错，所以这里再确认一次通知到底存不存在、是不是自己的
            Long count = baseMapper.selectCount(Wrappers.lambdaQuery(CommunityNotificationDO.class)
                    .eq(CommunityNotificationDO::getNotificationId, notificationId)
                    .eq(CommunityNotificationDO::getReceiverUid, uid));
            if (count == null || count == 0) {
                throw new ClientException(CommunityErrorCodeEnum.NOTIFICATION_NOT_EXIST);
            }
        }
    }

    @Override
    public void markAllRead() {
        Long uid = currentLoginUid();
        baseMapper.update(null, Wrappers.lambdaUpdate(CommunityNotificationDO.class)
                .set(CommunityNotificationDO::getReadFlag, CommunityConstant.NOTIFY_READ)
                .eq(CommunityNotificationDO::getReceiverUid, uid)
                .eq(CommunityNotificationDO::getReadFlag, CommunityConstant.NOTIFY_UNREAD));
    }

    /**
     * 内容摘要截断：通知里只是给用户看一眼，不需要存全文
     */
    private String truncate(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.length() <= CommunityConstant.NOTIFY_CONTENT_MAX_LENGTH
                ? trimmed : trimmed.substring(0, CommunityConstant.NOTIFY_CONTENT_MAX_LENGTH);
    }

    private Long currentLoginUid() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException(CommunityErrorCodeEnum.USER_NOT_LOGIN);
        }
        return Long.parseLong(userId);
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return CommunityConstant.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, CommunityConstant.MAX_PAGE_SIZE);
    }
}