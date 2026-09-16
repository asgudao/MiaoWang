package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityCommentDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityLikeDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityCommentMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityLikeMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.dto.req.CommentCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CommentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.CommentService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社区评论实现
 * <p>
 * 设计要点：
 * 1. 只做两级（一级评论 + 回复），回复二级评论时自动归到它的一级评论下 —— 避免无限层级带来的展示与查询复杂度；
 * 2. 列表按"一级评论分页 + 批量查回复"，不会出现子查询或 N+1（一次查询拿回本页全部回复）；
 * 3. 帖子的 comment_count 用 SQL 原子增减，与点赞/收藏的计数策略保持一致；
 * 4. 每条评论带 liked 标记（当前登录用户是否赞过），一次查询批量查出，前端据此高亮；
 * 5. 评论删除是物理删除（不做逻辑删除）：删掉不留残行，同时清掉这些评论的点赞关系。
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommunityCommentMapper, CommunityCommentDO> implements CommentService {

    private final CommunityPostMapper postMapper;
    private final CommunityLikeMapper likeMapper;

    @Override
    public CommentRespDTO createComment(CommentCreateReqDTO requestParam) {
        // 先校验登录、再校验资源，避免未登录请求能探测出帖子/评论是否存在
        Long uid = currentLoginUid();
        String username = UserContext.getUsername();
        if (requestParam == null || requestParam.getPostId() == null) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
        String content = StrUtil.trim(requestParam.getContent());
        if (StrUtil.isBlank(content)) {
            throw new ClientException(CommunityErrorCodeEnum.COMMENT_CONTENT_EMPTY);
        }
        if (content.length() > CommunityConstant.MAX_COMMENT_LENGTH) {
            throw new ClientException(CommunityErrorCodeEnum.COMMENT_CONTENT_TOO_LONG);
        }
        requirePublishedPost(requestParam.getPostId());

        Long parentId = requestParam.getParentId() == null
                ? CommunityConstant.COMMENT_PARENT_ROOT : requestParam.getParentId();
        Long replyUid = requestParam.getReplyUid();
        CommunityCommentDO parentComment = null;
        if (!CommunityConstant.COMMENT_PARENT_ROOT.equals(parentId)) {
            parentComment = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityCommentDO.class)
                    .eq(CommunityCommentDO::getCommentId, parentId)
                    .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
            // 父评论必须存在，且必须属于同一个帖子（否则可以跨帖子回复，前端展示会错乱）
            if (parentComment == null || !requestParam.getPostId().equals(parentComment.getPostId())) {
                throw new ClientException(CommunityErrorCodeEnum.COMMENT_PARENT_INVALID);
            }
            if (replyUid == null) {
                replyUid = parentComment.getUid();
            }
            // 回复的是二级评论时，把它挂到一级评论下（两级封顶）
            if (!CommunityConstant.COMMENT_PARENT_ROOT.equals(parentComment.getParentId())) {
                parentId = parentComment.getParentId();
            }
        }
        CommunityCommentDO commentDO = new CommunityCommentDO();
        commentDO.setPostId(requestParam.getPostId());
        commentDO.setUid(uid);
        commentDO.setUsername(username);
        commentDO.setParentId(parentId);
        commentDO.setReplyUid(replyUid);
        commentDO.setContent(content);
        commentDO.setLikeCount(0);
        // 当前没有审核环节，发表即发布；接入审核后改成 1（待审核），查询条件不用动
        commentDO.setStatus(CommunityConstant.COMMENT_STATUS_PUBLISHED);
        baseMapper.insert(commentDO);

        // 帖子评论数原子自增
        postMapper.update(null, Wrappers.lambdaUpdate(CommunityPostDO.class)
                .setSql("comment_count = comment_count + 1")
                .eq(CommunityPostDO::getPostId, requestParam.getPostId()));

        Map<Long, String> usernameMap = new HashMap<>(4);
        usernameMap.put(uid, username);
        if (parentComment != null) {
            usernameMap.putIfAbsent(parentComment.getUid(), parentComment.getUsername());
        }
        // 刚发出来的评论自己还没赞过，liked 直接给空集合
        return buildResp(commentDO, usernameMap, Collections.emptySet());
    }

    @Override
    public PageRespDTO<CommentRespDTO> pageComments(Long postId, Long pageNum, Long pageSize) {
        long page = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);
        // postId 为空按"没有评论"处理，避免抛出 Spring 原生的 400（响应体格式和项目不统一）
        if (postId == null) {
            return PageRespDTO.of(page, size, 0, Collections.emptyList());
        }
        // 第一层：分页查一级评论
        IPage<CommunityCommentDO> parentPage = baseMapper.selectPage(new Page<>(page, size),
                Wrappers.lambdaQuery(CommunityCommentDO.class)
                        .eq(CommunityCommentDO::getPostId, postId)
                        .eq(CommunityCommentDO::getParentId, CommunityConstant.COMMENT_PARENT_ROOT)
                        .eq(CommunityCommentDO::getStatus, CommunityConstant.COMMENT_STATUS_PUBLISHED)
                        .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                        .orderByDesc(CommunityCommentDO::getCreateTime)
                        .orderByDesc(CommunityCommentDO::getCommentId));
        List<CommunityCommentDO> parentList = parentPage.getRecords();
        if (CollUtil.isEmpty(parentList)) {
            return PageRespDTO.of(parentPage.getCurrent(), parentPage.getSize(), parentPage.getTotal(),
                    Collections.emptyList());
        }
        List<Long> parentIds = parentList.stream().map(CommunityCommentDO::getCommentId).collect(Collectors.toList());
        // 第二层：一次把本页一级评论下的回复全查出来（不是每条评论查一次）
        List<CommunityCommentDO> replyList = baseMapper.selectList(Wrappers.lambdaQuery(CommunityCommentDO.class)
                .in(CommunityCommentDO::getParentId, parentIds)
                .eq(CommunityCommentDO::getStatus, CommunityConstant.COMMENT_STATUS_PUBLISHED)
                .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .orderByAsc(CommunityCommentDO::getCommentId));
        // uid → username：本页一级评论作者 + 回复作者，用来推导"回复 @谁"
        Map<Long, String> usernameMap = new HashMap<>();
        parentList.forEach(comment -> usernameMap.putIfAbsent(comment.getUid(), comment.getUsername()));
        replyList.forEach(comment -> usernameMap.putIfAbsent(comment.getUid(), comment.getUsername()));
        // 本页哪些评论被当前登录用户赞过（一次查出来，不是每条评论查一次）
        Set<Long> likedIds = queryLikedCommentIds(parentList, replyList);
        Map<Long, List<CommunityCommentDO>> replyMap = replyList.stream()
                .collect(Collectors.groupingBy(CommunityCommentDO::getParentId));
        List<CommentRespDTO> records = parentList.stream().map(parent -> {
            CommentRespDTO respDTO = buildResp(parent, usernameMap, likedIds);
            List<CommunityCommentDO> children = replyMap.getOrDefault(parent.getCommentId(), Collections.emptyList());
            respDTO.setChildren(children.stream()
                    .map(child -> buildResp(child, usernameMap, likedIds))
                    .collect(Collectors.toList()));
            return respDTO;
        }).collect(Collectors.toList());
        return PageRespDTO.of(parentPage.getCurrent(), parentPage.getSize(), parentPage.getTotal(), records);
    }

    @Override
    public void deleteComment(Long commentId) {
        Long uid = currentLoginUid();
        CommunityCommentDO commentDO = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityCommentDO.class)
                .eq(CommunityCommentDO::getCommentId, commentId)
                .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (commentDO == null) {
            throw new ClientException(CommunityErrorCodeEnum.COMMENT_NOT_EXIST);
        }
        if (!uid.equals(commentDO.getUid())) {
            throw new ClientException(CommunityErrorCodeEnum.COMMENT_NO_PERMISSION);
        }
        // 删一级评论时把它下面的回复一起删，避免留下"找不到父级"的孤儿回复
        List<CommunityCommentDO> children = CommunityConstant.COMMENT_PARENT_ROOT.equals(commentDO.getParentId())
                ? baseMapper.selectList(Wrappers.lambdaQuery(CommunityCommentDO.class)
                        .eq(CommunityCommentDO::getParentId, commentId)
                        .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL))
                : Collections.emptyList();
        List<Long> deleteIds = children.stream()
                .map(CommunityCommentDO::getCommentId)
                .collect(Collectors.toList());
        // Collectors.toList() 返回的就是可变 ArrayList，把自己也放进去
        deleteIds.add(commentId);
        // 物理删除（不做逻辑删除）：评论删掉就是真的没了，表里不留 del_flag=1 的残行
        baseMapper.delete(Wrappers.lambdaQuery(CommunityCommentDO.class)
                .in(CommunityCommentDO::getCommentId, deleteIds));
        // 评论都没了，挂在它上面的点赞关系一起清掉，不给点赞表留垃圾
        likeMapper.delete(Wrappers.lambdaQuery(CommunityLikeDO.class)
                .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_COMMENT)
                .in(CommunityLikeDO::getTargetId, deleteIds));
        // 帖子评论数相应减少；gt 兜底，计数不会被减成负数
        postMapper.update(null, Wrappers.lambdaUpdate(CommunityPostDO.class)
                .setSql("comment_count = comment_count - " + deleteIds.size())
                .gt(CommunityPostDO::getCommentCount, deleteIds.size() - 1)
                .eq(CommunityPostDO::getPostId, commentDO.getPostId()));
    }

    /**
     * 查当前登录用户在本页评论里赞过哪些（未登录直接返回空集合，liked 恒为 false）
     */
    private Set<Long> queryLikedCommentIds(List<CommunityCommentDO> parentList, List<CommunityCommentDO> replyList) {
        Long uid = currentUidOrNull();
        if (uid == null) {
            return Collections.emptySet();
        }
        Set<Long> commentIds = parentList.stream()
                .map(CommunityCommentDO::getCommentId).collect(Collectors.toSet());
        replyList.forEach(comment -> commentIds.add(comment.getCommentId()));
        return likeMapper.selectList(Wrappers.lambdaQuery(CommunityLikeDO.class)
                        .eq(CommunityLikeDO::getUid, uid)
                        .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_COMMENT)
                        .in(CommunityLikeDO::getTargetId, commentIds))
                .stream()
                .map(CommunityLikeDO::getTargetId)
                .collect(Collectors.toSet());
    }

    /**
     * DO → DTO，并推导"回复 @谁"的昵称、填充 liked
     */
    private CommentRespDTO buildResp(CommunityCommentDO commentDO, Map<Long, String> usernameMap, Set<Long> likedIds) {
        CommentRespDTO respDTO = BeanUtil.toBean(commentDO, CommentRespDTO.class);
        if (commentDO.getReplyUid() != null) {
            respDTO.setReplyUsername(usernameMap.get(commentDO.getReplyUid()));
        }
        respDTO.setLiked(likedIds.contains(commentDO.getCommentId()));
        return respDTO;
    }

    private void requirePublishedPost(Long postId) {
        Long count = postMapper.selectCount(Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getPostId, postId)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (count == null || count == 0) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
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
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        return Long.parseLong(userId);
    }

    private long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? CommunityConstant.DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return CommunityConstant.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, CommunityConstant.MAX_PAGE_SIZE);
    }
}