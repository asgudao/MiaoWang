package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityBlockDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityFollowDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityNotificationDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityBlockMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityFollowMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityNotificationMapper;
import com.MapleLeaf.MiaoWang.dto.resp.BlockUserRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.BlockService;
import com.MapleLeaf.MiaoWang.service.UserInfoResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 拉黑实现
 * <p>
 * 关系表只存一行单向 (uid, block_uid)，"互相不可见"在查询时把两个方向合并（见 invisibleUids）；
 * 幂等靠唯一键 uk_uid_block，不靠"先查后写"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockServiceImpl extends ServiceImpl<CommunityBlockMapper, CommunityBlockDO> implements BlockService {

    private final CommunityFollowMapper followMapper;
    private final CommunityNotificationMapper notificationMapper;
    private final UserInfoResolver userInfoResolver;

    @Override
    public void block(Long blockUid) {
        Long uid = currentLoginUid();
        if (blockUid == null || blockUid <= 0) {
            throw new ClientException(CommunityErrorCodeEnum.BLOCK_USER_INVALID);
        }
        if (uid.equals(blockUid)) {
            throw new ClientException(CommunityErrorCodeEnum.BLOCK_SELF);
        }
        CommunityBlockDO blockDO = new CommunityBlockDO();
        blockDO.setUid(uid);
        blockDO.setBlockUid(blockUid);
        try {
            baseMapper.insert(blockDO);
        } catch (DuplicateKeyException e) {
            // 已经拉黑过了：幂等返回，重复点不该报错，也不必再清一遍（清理本身也是幂等的）
            log.debug("重复拉黑已忽略, uid={}, blockUid={}", uid, blockUid);
            return;
        }
        // 拉黑之后双方不该再有任何互动痕迹，顺手清掉两样：
        // 1. 双向关注关系（拉黑通常就包含"顺便取关"的意思）
        followMapper.delete(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getUid, uid)
                .eq(CommunityFollowDO::getFollowUid, blockUid));
        followMapper.delete(Wrappers.lambdaQuery(CommunityFollowDO.class)
                .eq(CommunityFollowDO::getUid, blockUid)
                .eq(CommunityFollowDO::getFollowUid, uid));
        // 2. 双向通知（留着就会在通知列表里看见被拉黑的人，与"看不见"自相矛盾）
        notificationMapper.delete(Wrappers.lambdaQuery(CommunityNotificationDO.class)
                .eq(CommunityNotificationDO::getReceiverUid, uid)
                .eq(CommunityNotificationDO::getActorUid, blockUid));
        notificationMapper.delete(Wrappers.lambdaQuery(CommunityNotificationDO.class)
                .eq(CommunityNotificationDO::getReceiverUid, blockUid)
                .eq(CommunityNotificationDO::getActorUid, uid));
    }

    @Override
    public void unblock(Long blockUid) {
        Long uid = currentLoginUid();
        int deleted = baseMapper.delete(Wrappers.lambdaQuery(CommunityBlockDO.class)
                .eq(CommunityBlockDO::getUid, uid)
                .eq(CommunityBlockDO::getBlockUid, blockUid));
        if (deleted == 0) {
            // 取消拉黑是幂等操作：本来就没拉黑，直接当成功
            log.debug("取消拉黑时关系不存在, uid={}, blockUid={}", uid, blockUid);
        }
    }

    @Override
    public CursorPageRespDTO<BlockUserRespDTO> pageBlocked(Long lastId, Long size) {
        Long uid = currentLoginUid();
        long limit = normalizeSize(size);
        // 游标用 block_id 倒序（自增，等价于"最近拉黑在前"），和关注列表同一套写法
        List<CommunityBlockDO> rows = baseMapper.selectList(Wrappers.lambdaQuery(CommunityBlockDO.class)
                .eq(CommunityBlockDO::getUid, uid)
                .lt(lastId != null, CommunityBlockDO::getBlockId, lastId)
                .orderByDesc(CommunityBlockDO::getBlockId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<CommunityBlockDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        if (CollUtil.isEmpty(pageList)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        List<Long> uids = pageList.stream().map(CommunityBlockDO::getBlockUid).collect(Collectors.toList());
        // 昵称批量解析一次（只有一条 SQL），不逐条查
        Map<Long, String> usernameMap = userInfoResolver.resolveUsernames(uids);
        List<BlockUserRespDTO> records = pageList.stream().map(row -> {
            BlockUserRespDTO respDTO = new BlockUserRespDTO();
            respDTO.setUid(row.getBlockUid());
            respDTO.setUsername(usernameMap.get(row.getBlockUid()));
            respDTO.setCreateTime(row.getCreateTime());
            return respDTO;
        }).collect(Collectors.toList());
        Long nextCursor = hasMore ? pageList.get(pageList.size() - 1).getBlockId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    @Override
    public Set<Long> invisibleUids(Long uid) {
        if (uid == null) {
            return Collections.emptySet();
        }
        Set<Long> invisible = new HashSet<>();
        // 我拉黑的人
        invisible.addAll(baseMapper.selectList(Wrappers.lambdaQuery(CommunityBlockDO.class)
                        .eq(CommunityBlockDO::getUid, uid))
                .stream().map(CommunityBlockDO::getBlockUid).collect(Collectors.toSet()));
        // 拉黑我的人
        invisible.addAll(baseMapper.selectList(Wrappers.lambdaQuery(CommunityBlockDO.class)
                        .eq(CommunityBlockDO::getBlockUid, uid))
                .stream().map(CommunityBlockDO::getUid).collect(Collectors.toSet()));
        return invisible;
    }

    @Override
    public boolean blockedBetween(Long oneUid, Long anotherUid) {
        if (oneUid == null || anotherUid == null || oneUid.equals(anotherUid)) {
            return false;
        }
        // 一个查询覆盖两个方向：(a→b) OR (b→a)
        Long count = baseMapper.selectCount(Wrappers.lambdaQuery(CommunityBlockDO.class)
                .and(wrapper -> wrapper
                        .eq(CommunityBlockDO::getUid, oneUid)
                        .eq(CommunityBlockDO::getBlockUid, anotherUid))
                .or(wrapper -> wrapper
                        .eq(CommunityBlockDO::getUid, anotherUid)
                        .eq(CommunityBlockDO::getBlockUid, oneUid)));
        return count != null && count > 0;
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