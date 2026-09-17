package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.service.MentionService;
import com.MapleLeaf.MiaoWang.service.NotificationService;
import com.MapleLeaf.MiaoWang.service.UsernameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @提及实现
 * <p>
 * 通知是"顺手做"的：解析不到用户名、写库失败都不该让发帖/评论失败，所以本类不向外抛异常
 * （与 NotificationService 的口径一致）。
 * <p>
 * 去重的两层含义：同一条内容里 @ 同一个人多次只提醒一次（唯一键也会挡）；
 * 拉黑关系与"自己 @ 自己"由 NotificationService 统一挡掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MentionServiceImpl implements MentionService {

    private final UsernameResolver usernameResolver;
    private final NotificationService notificationService;

    @Override
    public void notifyMentions(Long actorUid, String actorUsername, Collection<String> usernames,
                               Long postId, Long targetId, String summary) {
        if (actorUid == null || CollUtil.isEmpty(usernames)) {
            return;
        }
        Map<String, Long> uidMap;
        try {
            uidMap = usernameResolver.resolveUids(usernames);
        } catch (Exception e) {
            log.warn("@提及解析用户名失败，跳过通知, actorUid={}", actorUid, e);
            return;
        }
        if (CollUtil.isEmpty(uidMap)) {
            return;
        }
        // 一次操作里同一个人只提醒一次（解析已忽略大小写去重，这里再按 uid 兜一层）
        Set<Long> notifiedUids = new HashSet<>(uidMap.size());
        for (String username : usernames) {
            if (username == null) {
                continue;
            }
            Long receiverUid = uidMap.get(username.toLowerCase(Locale.ROOT));
            if (receiverUid == null || !notifiedUids.add(receiverUid)) {
                continue;
            }
            notificationService.notify(receiverUid, CommunityConstant.NOTIFY_TYPE_MENTION, actorUid,
                    actorUsername, targetId, postId, summary);
        }
    }
}