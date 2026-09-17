package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityFollowDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityFollowMapper;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.FollowUserRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.FollowService;
import com.MapleLeaf.MiaoWang.service.NotificationService;
import com.MapleLeaf.MiaoWang.service.UserInfoResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 关注实现
 * <p>
 * 关系表只存 (uid, follow_uid)，关注/取关都是物理删除：
 * 关注关系没有"历史价值"，取关就是没有了，留着 del_flag 只会让每次查询都要多带一个条件。
 * <p>
 * 幂等靠唯一键 uk_uid_follow，不靠"先查后写"——并发下先查后写会双写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl extends ServiceImpl<CommunityFollowMapper, CommunityFollowDO> implements FollowService {

    private final NotificationService notificationService;
    private final UserInfoResolver userInfoResolver;

    @Override
    public void follow(Long followUid) {
        Long uid = currentLoginUid();
        if (uid.equals(followUid)) {
            throw new ClientException(CommunityErrorCodeEnum.FOLLOW_SELF);
        }
        CommunityFollowDO followDO = new CommunityFollowDO();
        followDO.setUid(uid);
        followDO.setFollowUid(followUid);
        try {
            baseMapper.insert(followDO);
        } catch (DuplicateKeyException e) {
            // 已经关注过了：幂等返回成功，且不重复通知对方（反复点关注不该反复提醒）
            log.debug("重复关注已忽略, uid={}, followUid={}", uid, followUid);
            return;
        }
        // 关注成功才通知对方
        notificationService.notify(followUid, CommunityConstant.NOTIFY_TYPE_FOLLOW, uid,
                UserContext.getUsername(), CommunityConstant.NOTIFY_TARGET_NONE, CommunityConstant.NOTIFY_TARGET_NONE, null);
    }

    @Override
    public void unfollow(Long followUid) {
        Long uid = currentLoginUid();
        int deleted = baseMapper.delete(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getUid, uid)
                .eq(CommunityFollowDO::getFollowUid, followUid));
        if (deleted == 0) {
            // 取关是幂等操作：本来就没关注，直接当成功
            log.debug("取关时关系不存在, uid={}, followUid={}", uid, followUid);
        }
    }

    @Override
    public CursorPageRespDTO<FollowUserRespDTO> pageFollowing(Long uid, Long lastId, Long size) {
        Long targetUid = uid == null ? currentLoginUid() : uid;
        long limit = normalizeSize(size);
        List<CommunityFollowDO> rows = baseMapper.selectList(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getUid, targetUid)
                .lt(lastId != null, CommunityFollowDO::getFollowId, lastId)
                .orderByDesc(CommunityFollowDO::getFollowId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<CommunityFollowDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        if (CollUtil.isEmpty(pageList)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        // 关注列表里"我是第几个"没意义，uid 取被关注者
        List<Long> uids = pageList.stream().map(CommunityFollowDO::getFollowUid).collect(Collectors.toList());
        List<FollowUserRespDTO> records = buildRecords(pageList, uids);
        Long nextCursor = hasMore ? pageList.get(pageList.size() - 1).getFollowId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    @Override
    public CursorPageRespDTO<FollowUserRespDTO> pageFollower(Long uid, Long lastId, Long size) {
        Long targetUid = uid == null ? currentLoginUid() : uid;
        long limit = normalizeSize(size);
        List<CommunityFollowDO> rows = baseMapper.selectList(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getFollowUid, targetUid)
                .lt(lastId != null, CommunityFollowDO::getFollowId, lastId)
                .orderByDesc(CommunityFollowDO::getFollowId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<CommunityFollowDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        if (CollUtil.isEmpty(pageList)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        // 粉丝列表里 uid 取关注者（粉丝）
        List<Long> uids = pageList.stream().map(CommunityFollowDO::getUid).collect(Collectors.toList());
        List<FollowUserRespDTO> records = buildRecords(pageList, uids);
        Long nextCursor = hasMore ? pageList.get(pageList.size() - 1).getFollowId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    @Override
    public boolean isFollowing(Long uid, Long followUid) {
        if (uid == null || followUid == null) {
            return false;
        }
        Long count = baseMapper.selectCount(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getUid, uid)
                .eq(CommunityFollowDO::getFollowUid, followUid));
        return count != null && count > 0;
    }

    @Override
    public long countFollowing(Long uid) {
        if (uid == null) {
            return 0L;
        }
        Long count = baseMapper.selectCount(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getUid, uid));
        return count == null ? 0L : count;
    }

    @Override
    public long countFollower(Long uid) {
        if (uid == null) {
            return 0L;
        }
        Long count = baseMapper.selectCount(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getFollowUid, uid));
        return count == null ? 0L : count;
    }

    /**
     * 装配列表：昵称批量解析一次、当前用户的关注关系批量查一次，避免每条记录两次查询
     *
     * @param rows 关注关系行（顺序即返回顺序）
     * @param uids rows 对应的"要展示的用户uid"（关注列表=被关注者，粉丝列表=关注者）
     */
    private List<FollowUserRespDTO> buildRecords(List<CommunityFollowDO> rows, List<Long> uids) {
        Map<Long, String> usernameMap = userInfoResolver.resolveUsernames(uids);
        Set<Long> followedIds = queryMyFollowedIds(uids);
        return IntStream.range(0, rows.size())
                .mapToObj(index -> {
                    Long targetUid = uids.get(index);
                    FollowUserRespDTO respDTO = new FollowUserRespDTO();
                    respDTO.setUid(targetUid);
                    respDTO.setUsername(usernameMap.get(targetUid));
                    respDTO.setFollowed(followedIds.contains(targetUid));
                    respDTO.setCreateTime(rows.get(index).getCreateTime());
                    return respDTO;
                })
                .collect(Collectors.toList());
    }

    /**
     * 当前登录用户在这批 uid 里关注了谁（未登录返回空集合，"关注"按钮一律未关注态）
     */
    private Set<Long> queryMyFollowedIds(List<Long> uids) {
        Long myUid = currentUidOrNull();
        if (myUid == null || CollUtil.isEmpty(uids)) {
            return Collections.emptySet();
        }
        return baseMapper.selectList(Wrappers.lambdaQuery(CommunityFollowDO.class)
                        .eq(CommunityFollowDO::getUid, myUid)
                        .in(CommunityFollowDO::getFollowUid, uids))
                .stream()
                .map(CommunityFollowDO::getFollowUid)
                .collect(Collectors.toSet());
    }

    private Long currentLoginUid() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException(CommunityErrorCodeEnum.USER_NOT_LOGIN);
        }
        return Long.parseLong(userId);
    }

    private Long currentUidOrNull() {
        String userId = UserContext.getUserId();
        return StrUtil.isBlank(userId) ? null : Long.parseLong(userId);
    }

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return CommunityConstant.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, CommunityConstant.MAX_PAGE_SIZE);
    }
}