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
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.BlockService;
import com.MapleLeaf.MiaoWang.service.CommentService;
import com.MapleLeaf.MiaoWang.service.MentionService;
import com.MapleLeaf.MiaoWang.service.NotificationService;
import com.MapleLeaf.MiaoWang.service.SensitiveWordService;
import com.MapleLeaf.MiaoWang.support.ContentTagParser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 2. 列表**游标分页**而不是页码：评论新的在最前面，offset 分页在翻页期间遇到新增/删除会重复或漏数据，
 * 而且 LIMIT offset 越翻越慢。游标从"上一页最后一条"往下取，不受新增影响，深翻页一样快；
 * 3. 列表只带每条一级评论的前 N 条回复 + 回复总数，想看全部走回复的游标接口
 * —— 一条爆款评论下面可能有上千条回复，全带出来会把这一页的响应撑爆；
 * 4. 一次查询批量取回复预览（窗口函数）、一次查询批量取回复数、一次查询批量取点赞关系，全程没有 N+1；
 * 5. 评论删除是物理删除（不做逻辑删除）：删掉不留残行，同时清掉这些评论的点赞关系。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommunityCommentMapper, CommunityCommentDO> implements CommentService {

    private final CommunityPostMapper postMapper;
    private final CommunityLikeMapper likeMapper;
    private final NotificationService notificationService;
    private final BlockService blockService;
    private final SensitiveWordService sensitiveWordService;
    private final MentionService mentionService;

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
        checkSensitiveWords(uid, content);
        CommunityPostDO postDO = requirePublishedPost(requestParam.getPostId());
        // 拉黑关系里不能评论对方的内容（列表已经隐藏，但接口可以被直接调用）
        if (blockService.blockedBetween(uid, postDO.getUid())) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }

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

        // 发通知：一级评论通知楼主；回复通知"被回复的那条评论"的作者。
        // 回复时不再额外通知楼主 —— 楼主本来就会在自己的回复列表里看到，重复提醒只会变成骚扰。
        if (parentComment == null) {
            notificationService.notify(postDO.getUid(), CommunityConstant.NOTIFY_TYPE_COMMENT_POST, uid, username,
                    commentDO.getCommentId(), postDO.getPostId(), content);
        } else {
            notificationService.notify(parentComment.getUid(), CommunityConstant.NOTIFY_TYPE_REPLY_COMMENT, uid, username,
                    commentDO.getCommentId(), postDO.getPostId(), content);
        }

        // @提及：正文里 @ 到的人各提醒一次（评论里的提及 targetId 用评论 id，前端能定位到楼中楼）
        mentionService.notifyMentions(uid, username, ContentTagParser.parseMentions(null, content),
                postDO.getPostId(), commentDO.getCommentId(), content);

        Map<Long, String> usernameMap = new HashMap<>(4);
        usernameMap.put(uid, username);
        if (parentComment != null) {
            usernameMap.putIfAbsent(parentComment.getUid(), parentComment.getUsername());
        }
        // 刚发出来的评论自己还没赞过，liked 直接给空集合
        return buildResp(commentDO, usernameMap, Collections.emptySet());
    }

    @Override
    public CursorPageRespDTO<CommentRespDTO> pageComments(Long postId, Long lastId, Long size) {
        long limit = normalizeSize(size);
        // 拉黑过滤：与我互相不可见的用户，评论不进这一页
        Set<Long> invisibleUids = blockService.invisibleUids(currentUidOrNull());
        // postId 为空按"没有评论"处理，避免抛出 Spring 原生的 400（响应体格式和项目不统一）
        if (postId == null) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        // 一级评论按 comment_id 倒序（= 最新的在最前）；游标是"比上一页最后一条更小"
        // 多取一条用于判断还有没有下一页，这样不用对大表做 COUNT
        List<CommunityCommentDO> rows = baseMapper.selectList(Wrappers.lambdaQuery(CommunityCommentDO.class)
                .eq(CommunityCommentDO::getPostId, postId)
                .eq(CommunityCommentDO::getParentId, CommunityConstant.COMMENT_PARENT_ROOT)
                .eq(CommunityCommentDO::getStatus, CommunityConstant.COMMENT_STATUS_PUBLISHED)
                .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .notIn(CollUtil.isNotEmpty(invisibleUids), CommunityCommentDO::getUid, invisibleUids)
                .lt(lastId != null, CommunityCommentDO::getCommentId, lastId)
                .orderByDesc(CommunityCommentDO::getCommentId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<CommunityCommentDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        if (CollUtil.isEmpty(pageList)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        List<Long> parentIds = pageList.stream().map(CommunityCommentDO::getCommentId).collect(Collectors.toList());
        // 回复预览：一次查出这些一级评论各自的前 N 条回复（窗口函数，不是每条评论查一次）
        List<CommunityCommentDO> previewReplies = baseMapper.selectPreviewReplies(parentIds,
                CommunityConstant.COMMENT_STATUS_PUBLISHED, CommunityConstant.COMMENT_REPLY_PREVIEW_COUNT);
        // 回复总数：一次分组统计出来，前端据此显示"查看全部 N 条回复"
        Map<Long, Integer> replyCountMap = queryReplyCounts(parentIds);
        Map<Long, String> usernameMap = buildUsernameMap(pageList, previewReplies);
        Set<Long> likedIds = queryLikedCommentIds(pageList, previewReplies);
        Map<Long, List<CommunityCommentDO>> replyMap = previewReplies.stream()
                .collect(Collectors.groupingBy(CommunityCommentDO::getParentId));
        List<CommentRespDTO> records = pageList.stream().map(parent -> {
            CommentRespDTO respDTO = buildResp(parent, usernameMap, likedIds);
            respDTO.setReplyCount(replyCountMap.getOrDefault(parent.getCommentId(), 0));
            respDTO.setChildren(replyMap.getOrDefault(parent.getCommentId(), Collections.emptyList()).stream()
                    // 回复预览也要过滤拉黑对象；replyCount 仍是原始条数（精确计数要过滤就得把条件塞进那条窗口函数 SQL，先记账）
                    .filter(child -> !invisibleUids.contains(child.getUid()))
                    .map(child -> buildResp(child, usernameMap, likedIds))
                    .collect(Collectors.toList()));
            return respDTO;
        }).collect(Collectors.toList());
        Long nextCursor = hasMore ? records.get(records.size() - 1).getCommentId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    @Override
    public CursorPageRespDTO<CommentRespDTO> pageReplies(Long commentId, Long lastId, Long size) {
        long limit = normalizeSize(size);
        // 拉黑过滤：与我互相不可见的用户，回复不展示
        Set<Long> invisibleUids = blockService.invisibleUids(currentUidOrNull());
        if (commentId == null) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        // 回复按 comment_id 正序（最早的在最前，符合对话阅读顺序）；游标是"比上一页最后一条更大"
        List<CommunityCommentDO> rows = baseMapper.selectList(Wrappers.lambdaQuery(CommunityCommentDO.class)
                .eq(CommunityCommentDO::getParentId, commentId)
                .eq(CommunityCommentDO::getStatus, CommunityConstant.COMMENT_STATUS_PUBLISHED)
                .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .notIn(CollUtil.isNotEmpty(invisibleUids), CommunityCommentDO::getUid, invisibleUids)
                .gt(lastId != null, CommunityCommentDO::getCommentId, lastId)
                .orderByAsc(CommunityCommentDO::getCommentId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<CommunityCommentDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        if (CollUtil.isEmpty(pageList)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        // "回复 @谁" 需要一级评论作者 + 本页回复作者，所以把父评论也查出来
        List<CommunityCommentDO> parentList = baseMapper.selectList(Wrappers.lambdaQuery(CommunityCommentDO.class)
                .eq(CommunityCommentDO::getCommentId, commentId)
                .eq(CommunityCommentDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        Map<Long, String> usernameMap = buildUsernameMap(parentList, pageList);
        Set<Long> likedIds = queryLikedCommentIds(parentList, pageList);
        List<CommentRespDTO> records = pageList.stream()
                .map(comment -> buildResp(comment, usernameMap, likedIds))
                .collect(Collectors.toList());
        Long nextCursor = hasMore ? records.get(records.size() - 1).getCommentId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
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
            // 不是自己的评论时，只有该帖楼主能删（楼主在自家帖子里有管理权），其余人一律拒绝
            CommunityPostDO postDO = postMapper.selectOne(Wrappers.lambdaQuery(CommunityPostDO.class)
                    .eq(CommunityPostDO::getPostId, commentDO.getPostId())
                    .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
            if (postDO == null || !uid.equals(postDO.getUid())) {
                throw new ClientException(CommunityErrorCodeEnum.COMMENT_NO_PERMISSION);
            }
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
     * 批量统计这些一级评论各有几条回复（一次分组查询，不是每条评论查一次）
     */
    private Map<Long, Integer> queryReplyCounts(List<Long> parentIds) {
        List<Map<String, Object>> rows = baseMapper.selectMaps(Wrappers.<CommunityCommentDO>query()
                .select("parent_id AS parent_id", "COUNT(*) AS reply_count")
                .in("parent_id", parentIds)
                .eq("status", CommunityConstant.COMMENT_STATUS_PUBLISHED)
                .eq("del_flag", CommunityConstant.DEL_FLAG_NORMAL)
                .groupBy("parent_id"));
        Map<Long, Integer> replyCountMap = new HashMap<>(parentIds.size());
        for (Map<String, Object> row : rows) {
            Object parentId = row.get("parent_id");
            Object replyCount = row.get("reply_count");
            if (parentId instanceof Number && replyCount instanceof Number) {
                replyCountMap.put(((Number) parentId).longValue(), ((Number) replyCount).intValue());
            }
        }
        return replyCountMap;
    }

    /**
     * 查当前登录用户在这些评论里赞过哪些（未登录直接返回空集合，liked 恒为 false）
     */
    private Set<Long> queryLikedCommentIds(List<CommunityCommentDO> parentList, List<CommunityCommentDO> replyList) {
        Long uid = currentUidOrNull();
        if (uid == null) {
            return Collections.emptySet();
        }
        Set<Long> commentIds = parentList.stream()
                .map(CommunityCommentDO::getCommentId).collect(Collectors.toSet());
        replyList.forEach(comment -> commentIds.add(comment.getCommentId()));
        if (commentIds.isEmpty()) {
            return Collections.emptySet();
        }
        return likeMapper.selectList(Wrappers.lambdaQuery(CommunityLikeDO.class)
                        .eq(CommunityLikeDO::getUid, uid)
                        .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_COMMENT)
                        .in(CommunityLikeDO::getTargetId, commentIds))
                .stream()
                .map(CommunityLikeDO::getTargetId)
                .collect(Collectors.toSet());
    }

    /**
     * uid → username：用来推导"回复 @谁"（被回复人的昵称取自帖子内的评论）
     */
    private Map<Long, String> buildUsernameMap(List<CommunityCommentDO> first, List<CommunityCommentDO> second) {
        Map<Long, String> usernameMap = new HashMap<>();
        first.forEach(comment -> usernameMap.putIfAbsent(comment.getUid(), comment.getUsername()));
        second.forEach(comment -> usernameMap.putIfAbsent(comment.getUid(), comment.getUsername()));
        return usernameMap;
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

    /**
     * 评论前校验帖子存在且已发布；返回帖子对象供发通知取楼主 uid 用
     */
    private CommunityPostDO requirePublishedPost(Long postId) {
        CommunityPostDO postDO = postMapper.selectOne(Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getPostId, postId)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (postDO == null) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
        return postDO;
    }

    /**
     * 敏感词校验：命中就拒绝发表
     * <p>
     * 命中的词只记日志、不回显（回显等于把词库交代出去）。
     */
    private void checkSensitiveWords(Long uid, String content) {
        List<String> hits = sensitiveWordService.matches(content);
        if (CollUtil.isNotEmpty(hits)) {
            log.warn("评论内容命中敏感词, uid={}, hits={}", uid, hits);
            throw new ClientException(CommunityErrorCodeEnum.SENSITIVE_WORD);
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

    private long normalizeSize(Long size) {
        if (size == null || size < 1) {
            return CommunityConstant.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, CommunityConstant.MAX_PAGE_SIZE);
    }
}