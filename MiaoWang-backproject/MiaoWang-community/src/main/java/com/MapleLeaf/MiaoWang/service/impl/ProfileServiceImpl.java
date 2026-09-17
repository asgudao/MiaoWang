package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.dto.resp.PetBriefRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserProfileRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.openfegin.pet.PetProfileFeign;
import com.MapleLeaf.MiaoWang.service.FollowService;
import com.MapleLeaf.MiaoWang.service.ProfileService;
import com.MapleLeaf.MiaoWang.service.UserInfoResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 个人主页实现
 * <p>
 * 头部那几个数字都是"算出来的"，不是冗余在用户表里的计数器：
 * 帖子数 + 获赞总数一条聚合 SQL 出结果（走 uid 索引，只扫这个人的帖子），
 * 关注数/粉丝数在关注表上 COUNT（同样是索引 COUNT）。
 * <p>
 * 量级再大就轮到缓存上场（计数器 / Redis 未读数），那是后面做高并发的靶子。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final CommunityPostMapper postMapper;
    private final FollowService followService;
    private final UserInfoResolver userInfoResolver;
    private final PetProfileFeign petServiceFeign;

    @Override
    public UserProfileRespDTO getProfile(Long uid) {
        Long targetUid = uid == null ? currentLoginUid() : uid;
        // 帖子数与获赞总数合并成一次聚合查询，别拆成两条 SQL 扫两遍
        List<Map<String, Object>> rows = postMapper.selectMaps(Wrappers.<CommunityPostDO>query()
                .select("COUNT(*) AS post_count", "CAST(IFNULL(SUM(like_count), 0) AS SIGNED) AS like_received")
                .eq("uid", targetUid)
                .eq("status", CommunityConstant.POST_STATUS_PUBLISHED)
                .eq("del_flag", CommunityConstant.DEL_FLAG_NORMAL));
        UserProfileRespDTO respDTO = new UserProfileRespDTO();
        respDTO.setUid(targetUid);
        respDTO.setPostCount(readLong(rows, "post_count"));
        respDTO.setLikeReceivedCount(readLong(rows, "like_received"));
        respDTO.setFollowingCount(followService.countFollowing(targetUid));
        respDTO.setFollowerCount(followService.countFollower(targetUid));
        Long myUid = currentUidOrNull();
        boolean self = myUid != null && myUid.equals(targetUid);
        respDTO.setSelf(self);
        // 看自己主页时"已关注"没有意义，恒为 false
        respDTO.setFollowed(!self && myUid != null && followService.isFollowing(myUid, targetUid));
        respDTO.setUsername(userInfoResolver.resolveUsernames(Collections.singletonList(targetUid)).get(targetUid));
        return respDTO;
    }

    @Override
    public List<PetBriefRespDTO> listPets(Long uid) {
        if (uid == null) {
            return Collections.emptyList();
        }
        try {
            Result<List<PetBriefRespDTO>> result = petServiceFeign.getPetsByUid(uid);
            if (result == null || !result.isSuccess() || result.getData() == null) {
                log.warn("宠物服务返回异常, uid={}, code={}, message={}", uid,
                        result == null ? null : result.getCode(), result == null ? null : result.getMessage());
                return Collections.emptyList();
            }
            return result.getData();
        } catch (Exception e) {
            // 跨服务调用要能降级：宠物服务没起来不该让个人主页整个 500，
            // 前端拿到空列表渲染"还没有宠物"，比整页报错体面得多
            log.warn("调用宠物服务失败，个人主页降级为空宠物列表, uid={}", uid, e);
            return Collections.emptyList();
        }
    }

    /**
     * 聚合查询的结果只有一行，取不到就返回 0（新用户没有任何帖子）
     */
    private long readLong(List<Map<String, Object>> rows, String column) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Object value = rows.get(0).get(column);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
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
}