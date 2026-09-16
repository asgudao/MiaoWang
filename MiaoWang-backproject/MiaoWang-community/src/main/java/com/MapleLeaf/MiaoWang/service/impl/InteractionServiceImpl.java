package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityCommentDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityFavoriteDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityLikeDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityCommentMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityFavoriteMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityLikeMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.InteractionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 点赞 / 收藏实现
 * <p>
 * 幂等靠数据库唯一键 (uid, target_type, target_id)，不靠先查后写：
 * "先 select 再 insert"在并发下会双写，唯一键 + 捕获冲突异常才是可靠的。
 * <p>
 * 帖子赞和评论赞共用同一张表，只是 target_type 不同（1=帖子 2=评论），
 * 所以两类目标的幂等逻辑完全一致，计数分别落在 post.like_count / comment.like_count。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractionServiceImpl implements InteractionService {

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityLikeMapper likeMapper;
    private final CommunityFavoriteMapper favoriteMapper;

    @Override
    public void likePost(Long postId) {
        // 先校验登录、再校验帖子是否存在：顺序反了会让未登录请求能探测出哪些帖子 id 存在
        Long uid = currentLoginUid();
        requirePublishedPost(postId);
        CommunityLikeDO likeDO = new CommunityLikeDO();
        likeDO.setUid(uid);
        likeDO.setTargetType(CommunityConstant.TARGET_TYPE_POST);
        likeDO.setTargetId(postId);
        try {
            likeMapper.insert(likeDO);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突 = 已经点过赞：幂等返回成功，不重复加计数（连点/刷新都不会把数字刷高）
            log.debug("重复点赞已忽略, uid={}, postId={}", uid, postId);
            return;
        }
        // 计数用 SQL 原子自增：不用"查出来 +1 再写回"，避免并发丢更新
        postMapper.update(null, Wrappers.lambdaUpdate(CommunityPostDO.class)
                .setSql("like_count = like_count + 1")
                .eq(CommunityPostDO::getPostId, postId));
    }

    @Override
    public void unlikePost(Long postId) {
        Long uid = currentLoginUid();
        int deleted = likeMapper.delete(Wrappers.lambdaQuery(CommunityLikeDO.class)
                .eq(CommunityLikeDO::getUid, uid)
                .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                .eq(CommunityLikeDO::getTargetId, postId));
        if (deleted > 0) {
            // 只有真的删掉了关系记录才减计数；gt(likeCount, 0) 兜底，计数不会被减成负数
            postMapper.update(null, Wrappers.lambdaUpdate(CommunityPostDO.class)
                    .setSql("like_count = like_count - 1")
                    .gt(CommunityPostDO::getLikeCount, 0)
                    .eq(CommunityPostDO::getPostId, postId));
        }
    }

    @Override
    public void favoritePost(Long postId) {
        Long uid = currentLoginUid();
        requirePublishedPost(postId);
        CommunityFavoriteDO favoriteDO = new CommunityFavoriteDO();
        favoriteDO.setUid(uid);
        favoriteDO.setTargetType(CommunityConstant.TARGET_TYPE_POST);
        favoriteDO.setTargetId(postId);
        try {
            favoriteMapper.insert(favoriteDO);
        } catch (DuplicateKeyException e) {
            log.debug("重复收藏已忽略, uid={}, postId={}", uid, postId);
            return;
        }
        postMapper.update(null, Wrappers.lambdaUpdate(CommunityPostDO.class)
                .setSql("favorite_count = favorite_count + 1")
                .eq(CommunityPostDO::getPostId, postId));
    }

    @Override
    public void unfavoritePost(Long postId) {
        Long uid = currentLoginUid();
        int deleted = favoriteMapper.delete(Wrappers.lambdaQuery(CommunityFavoriteDO.class)
                .eq(CommunityFavoriteDO::getUid, uid)
                .eq(CommunityFavoriteDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                .eq(CommunityFavoriteDO::getTargetId, postId));
        if (deleted > 0) {
            postMapper.update(null, Wrappers.lambdaUpdate(CommunityPostDO.class)
                    .setSql("favorite_count = favorite_count - 1")
                    .gt(CommunityPostDO::getFavoriteCount, 0)
                    .eq(CommunityPostDO::getPostId, postId));
        }
    }

    @Override
    public void likeComment(Long commentId) {
        Long uid = currentLoginUid();
        requirePublishedComment(commentId);
        CommunityLikeDO likeDO = new CommunityLikeDO();
        likeDO.setUid(uid);
        likeDO.setTargetType(CommunityConstant.TARGET_TYPE_COMMENT);
        likeDO.setTargetId(commentId);
        try {
            likeMapper.insert(likeDO);
        } catch (DuplicateKeyException e) {
            log.debug("重复点赞评论已忽略, uid={}, commentId={}", uid, commentId);
            return;
        }
        commentMapper.update(null, Wrappers.lambdaUpdate(CommunityCommentDO.class)
                .setSql("like_count = like_count + 1")
                .eq(CommunityCommentDO::getCommentId, commentId));
    }

    @Override
    public void unlikeComment(Long commentId) {
        Long uid = currentLoginUid();
        int deleted = likeMapper.delete(Wrappers.lambdaQuery(CommunityLikeDO.class)
                .eq(CommunityLikeDO::getUid, uid)
                .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_COMMENT)
                .eq(CommunityLikeDO::getTargetId, commentId));
        if (deleted > 0) {
            commentMapper.update(null, Wrappers.lambdaUpdate(CommunityCommentDO.class)
                    .setSql("like_count = like_count - 1")
                    .gt(CommunityCommentDO::getLikeCount, 0)
                    .eq(CommunityCommentDO::getCommentId, commentId));
        }
    }

    /**
     * 互动前校验目标帖子存在且已发布（删掉的帖子不能再点赞/收藏）
     */
    private void requirePublishedPost(Long postId) {
        Long count = postMapper.selectCount(Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getPostId, postId)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (count == null || count == 0) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
    }

    /**
     * 点赞评论前校验评论存在（删掉的评论不能再点赞）
     */
    private void requirePublishedComment(Long commentId) {
        Long count = commentMapper.selectCount(Wrappers.lambdaQuery(CommunityCommentDO.class)
                .eq(CommunityCommentDO::getCommentId, commentId)
                .eq(CommunityCommentDO::getStatus, CommunityConstant.COMMENT_STATUS_PUBLISHED)
                .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (count == null || count == 0) {
            throw new ClientException(CommunityErrorCodeEnum.COMMENT_NOT_EXIST);
        }
    }

    private Long currentLoginUid() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException(CommunityErrorCodeEnum.USER_NOT_LOGIN);
        }
        return Long.parseLong(userId);
    }
}