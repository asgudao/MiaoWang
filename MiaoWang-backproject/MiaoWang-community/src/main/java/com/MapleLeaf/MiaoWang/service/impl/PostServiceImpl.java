package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.biz.user.UserContext;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityFavoriteDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityLikeDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostImageDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunityPostVideoDO;
import com.MapleLeaf.MiaoWang.dao.entity.CommunitySectionDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityFavoriteMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityLikeMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostImageMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostVideoMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunitySectionMapper;
import com.MapleLeaf.MiaoWang.dto.req.PostCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PostImageReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PostVideoReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PostUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostDetailRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostImageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostVideoRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.TopicRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.BlockService;
import com.MapleLeaf.MiaoWang.service.MentionService;
import com.MapleLeaf.MiaoWang.service.PostService;
import com.MapleLeaf.MiaoWang.service.SensitiveWordService;
import com.MapleLeaf.MiaoWang.service.TopicService;
import com.MapleLeaf.MiaoWang.support.ContentTagParser;
import com.MapleLeaf.MiaoWang.support.SqlLikeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社区帖子实现
 * <p>
 * 计数策略：帖子表上冗余 like_count / favorite_count / comment_count，
 * 列表页只查帖子表就能展示计数，不用去 COUNT 点赞/收藏那两张随规模增长的明细表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl extends ServiceImpl<CommunityPostMapper, CommunityPostDO> implements PostService {

    private final CommunityPostImageMapper postImageMapper;
    private final CommunityPostVideoMapper postVideoMapper;
    private final CommunitySectionMapper sectionMapper;
    private final CommunityLikeMapper likeMapper;
    private final CommunityFavoriteMapper favoriteMapper;
    private final BlockService blockService;
    private final SensitiveWordService sensitiveWordService;
    private final TopicService topicService;
    private final MentionService mentionService;

    @Override
    public PostDetailRespDTO createPost(PostCreateReqDTO requestParam) {
        Long uid = currentLoginUid();
        String username = UserContext.getUsername();
        if (requestParam == null || requestParam.getSectionId() == null) {
            throw new ClientException(CommunityErrorCodeEnum.POST_SECTION_REQUIRED);
        }
        // 版块必须存在且启用，避免发到已停用的版块
        CommunitySectionDO sectionDO = sectionMapper.selectOne(Wrappers.lambdaQuery(CommunitySectionDO.class)
                .eq(CommunitySectionDO::getSectionId, requestParam.getSectionId())
                .eq(CommunitySectionDO::getStatus, 1)
                .eq(CommunitySectionDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (sectionDO == null) {
            throw new ClientException(CommunityErrorCodeEnum.SECTION_NOT_EXIST);
        }
        // 过滤掉没有 url 的空图片项，再做数量校验
        List<PostImageReqDTO> images = requestParam.getImages() == null ? Collections.emptyList()
                : requestParam.getImages().stream()
                .filter(image -> image != null && StrUtil.isNotBlank(image.getUrl()))
                .collect(Collectors.toList());
        if (images.size() > CommunityConstant.MAX_POST_IMAGE_COUNT) {
            throw new ClientException(CommunityErrorCodeEnum.POST_IMAGE_TOO_MANY);
        }
        // 视频和图片同一套规则：最多 9 个、顺序即展示顺序、可以和图片共存
        List<PostVideoReqDTO> videos = requestParam.getVideos() == null ? Collections.emptyList()
                : requestParam.getVideos().stream()
                .filter(videoItem -> videoItem != null && StrUtil.isNotBlank(videoItem.getUrl()))
                .collect(Collectors.toList());
        if (videos.size() > CommunityConstant.MAX_POST_VIDEO_COUNT) {
            throw new ClientException(CommunityErrorCodeEnum.POST_VIDEO_TOO_MANY);
        }
        // url + objectKey 必须成对出现：只给一半说明上传没走完；objectKey 是将来清理 MinIO 的凭据，不能省
        for (PostVideoReqDTO videoItem : videos) {
            if (StrUtil.isBlank(videoItem.getObjectKey())) {
                throw new ClientException(CommunityErrorCodeEnum.VIDEO_PARAM_INVALID);
            }
        }
        if (images.isEmpty() && videos.isEmpty()
                && StrUtil.isBlank(requestParam.getTitle()) && StrUtil.isBlank(requestParam.getContent())) {
            throw new ClientException(CommunityErrorCodeEnum.POST_CONTENT_EMPTY);
        }
        // 敏感词在"写库之前"拦一道：标题和正文都过一遍
        checkSensitiveWords(uid, requestParam.getTitle(), requestParam.getContent());
        CommunityPostDO postDO = new CommunityPostDO();
        postDO.setSectionId(sectionDO.getSectionId());
        postDO.setUid(uid);
        postDO.setUsername(username);
        postDO.setTitle(StrUtil.trim(requestParam.getTitle()));
        postDO.setContent(requestParam.getContent());
        // 第 1 张图当封面，冗余到帖子表：列表页免联图片表（视频不当封面，前端直接用 <video> 渲染）
        postDO.setCoverUrl(images.isEmpty() ? null : images.get(0).getUrl());
        // 当前没有审核环节，发帖即发布；接入审核后这里改成 POST_STATUS_AUDITING
        postDO.setStatus(CommunityConstant.POST_STATUS_PUBLISHED);
        postDO.setIsTop(0);
        postDO.setViewCount(0);
        postDO.setLikeCount(0);
        postDO.setFavoriteCount(0);
        postDO.setCommentCount(0);
        // MP 会把自增主键回填到 postDO.postId
        baseMapper.insert(postDO);

        for (int i = 0; i < images.size(); i++) {
            PostImageReqDTO image = images.get(i);
            CommunityPostImageDO imageDO = new CommunityPostImageDO();
            imageDO.setPostId(postDO.getPostId());
            imageDO.setUrl(image.getUrl());
            imageDO.setObjectKey(image.getObjectKey());
            imageDO.setSort(i);
            postImageMapper.insert(imageDO);
        }
        for (int i = 0; i < videos.size(); i++) {
            PostVideoReqDTO videoItem = videos.get(i);
            CommunityPostVideoDO videoDO = new CommunityPostVideoDO();
            videoDO.setPostId(postDO.getPostId());
            videoDO.setUrl(videoItem.getUrl());
            videoDO.setObjectKey(videoItem.getObjectKey());
            videoDO.setSort(i);
            postVideoMapper.insert(videoDO);
        }
        // 话题与 @提及都在写库之后顺手做：解析失败、通知发不出去，都不该让发帖失败
        topicService.syncPostTopics(postDO.getPostId(), postDO.getTitle(), postDO.getContent());
        mentionService.notifyMentions(uid, username,
                ContentTagParser.parseMentions(postDO.getTitle(), postDO.getContent()),
                postDO.getPostId(), postDO.getPostId(),
                StrUtil.blankToDefault(postDO.getTitle(), postDO.getContent()));
        return buildDetail(postDO);
    }

    @Override
    public PageRespDTO<PostRespDTO> pagePosts(Long sectionId, Long pageNum, Long pageSize) {
        long page = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);
        LambdaQueryWrapper<CommunityPostDO> queryWrapper = Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL);
        if (sectionId != null) {
            queryWrapper.eq(CommunityPostDO::getSectionId, sectionId);
        }
        // 拉黑过滤：与我互相不可见的用户，他的帖子不出现在列表里（含我拉黑的 + 拉黑我的）
        Set<Long> invisibleUids = blockService.invisibleUids(currentUidOrNull());
        queryWrapper.notIn(CollUtil.isNotEmpty(invisibleUids), CommunityPostDO::getUid, invisibleUids);
        // 置顶优先，再按时间倒序（postId 自增，等价于发布时间倒序，比 create_time 更省一次排序）
        queryWrapper.orderByDesc(CommunityPostDO::getIsTop).orderByDesc(CommunityPostDO::getPostId);
        IPage<CommunityPostDO> postPage = baseMapper.selectPage(new Page<>(page, size), queryWrapper);
        return PageRespDTO.of(postPage.getCurrent(), postPage.getSize(), postPage.getTotal(),
                toRespList(postPage.getRecords(), currentUidOrNull()));
    }

    @Override
    public PostDetailRespDTO getPostDetail(Long postId) {
        CommunityPostDO postDO = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getPostId, postId)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (postDO == null) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
        // 互相拉黑的内容按"不存在"处理：不暴露"这条帖子存在但你看不到"（也顺便不计浏览量）
        if (blockService.blockedBetween(currentUidOrNull(), postDO.getUid())) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
        // 浏览量原子自增（交给数据库加，不用读改写）；后续亮点期换成 Redis 计数 + 异步落库
        baseMapper.update(null, Wrappers.lambdaUpdate(CommunityPostDO.class)
                .setSql("view_count = view_count + 1")
                .eq(CommunityPostDO::getPostId, postId));
        postDO.setViewCount(postDO.getViewCount() == null ? 1 : postDO.getViewCount() + 1);
        return buildDetail(postDO);
    }

    @Override
    public void deletePost(Long postId) {
        Long uid = currentLoginUid();
        CommunityPostDO postDO = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getPostId, postId)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (postDO == null) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
        if (!uid.equals(postDO.getUid())) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NO_PERMISSION);
        }
        // 逻辑删除帖子；MinIO 里的图片对象先保留（误删不可恢复），对象清理放到完善期
        postDO.setDelFlag(CommunityConstant.DEL_FLAG_DELETED);
        baseMapper.updateById(postDO);
        postImageMapper.update(null, Wrappers.lambdaUpdate(CommunityPostImageDO.class)
                .set(CommunityPostImageDO::getDelFlag, CommunityConstant.DEL_FLAG_DELETED)
                .eq(CommunityPostImageDO::getPostId, postId));
        // 话题关联要摘掉并回退计数，否则话题的 post_count 会随着删帖只涨不落
        topicService.removePostTopics(postId);
    }

    @Override
    public PostDetailRespDTO updatePost(Long postId, PostUpdateReqDTO requestParam) {
        Long uid = currentLoginUid();
        if (requestParam == null) {
            throw new ClientException(CommunityErrorCodeEnum.POST_CONTENT_EMPTY);
        }
        CommunityPostDO postDO = baseMapper.selectOne(Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getPostId, postId)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        if (postDO == null) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NOT_EXIST);
        }
        if (!uid.equals(postDO.getUid())) {
            throw new ClientException(CommunityErrorCodeEnum.POST_NO_PERMISSION);
        }
        // 改之前的话题 / 提及先留一份：话题要整份重建，提及只提醒"这次新加进来的"
        String oldTitle = postDO.getTitle();
        String oldContent = postDO.getContent();
        // title/content 传 null = 不改；传空串 = 清空（两件事，别混）
        String title = requestParam.getTitle() == null ? postDO.getTitle() : StrUtil.trim(requestParam.getTitle());
        String content = requestParam.getContent() == null ? postDO.getContent() : requestParam.getContent();
        // images 传 null = 不动图片；传数组（含空数组）= 整份替换
        List<PostImageReqDTO> newImages = requestParam.getImages() == null ? null
                : requestParam.getImages().stream()
                .filter(image -> image != null && StrUtil.isNotBlank(image.getUrl()))
                .collect(Collectors.toList());
        if (newImages != null && newImages.size() > CommunityConstant.MAX_POST_IMAGE_COUNT) {
            throw new ClientException(CommunityErrorCodeEnum.POST_IMAGE_TOO_MANY);
        }
        // videos 传 null = 不动视频；传数组（含空数组）= 整份替换，和 images 完全同一条规则
        List<PostVideoReqDTO> newVideos = requestParam.getVideos() == null ? null
                : requestParam.getVideos().stream()
                .filter(videoItem -> videoItem != null && StrUtil.isNotBlank(videoItem.getUrl()))
                .collect(Collectors.toList());
        if (newVideos != null) {
            if (newVideos.size() > CommunityConstant.MAX_POST_VIDEO_COUNT) {
                throw new ClientException(CommunityErrorCodeEnum.POST_VIDEO_TOO_MANY);
            }
            for (PostVideoReqDTO videoItem : newVideos) {
                if (StrUtil.isBlank(videoItem.getObjectKey())) {
                    throw new ClientException(CommunityErrorCodeEnum.VIDEO_PARAM_INVALID);
                }
            }
        }
        // "内容不能同时为空"要按改完后的样子判断：只清正文又不带图/视频，帖子里就什么都不剩了
        boolean imageEmpty;
        if (newImages == null) {
            Long imageCount = postImageMapper.selectCount(Wrappers.lambdaQuery(CommunityPostImageDO.class)
                    .eq(CommunityPostImageDO::getPostId, postDO.getPostId())
                    .eq(CommunityPostImageDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
            imageEmpty = imageCount == null || imageCount == 0;
        } else {
            imageEmpty = newImages.isEmpty();
        }
        // 视频同理：不传就看库里现在有几个，传了就看替换后的份数
        boolean videoEmpty;
        if (newVideos == null) {
            Long videoCount = postVideoMapper.selectCount(Wrappers.lambdaQuery(CommunityPostVideoDO.class)
                    .eq(CommunityPostVideoDO::getPostId, postDO.getPostId())
                    .eq(CommunityPostVideoDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
            videoEmpty = videoCount == null || videoCount == 0;
        } else {
            videoEmpty = newVideos.isEmpty();
        }
        if (imageEmpty && videoEmpty && StrUtil.isBlank(title) && StrUtil.isBlank(content)) {
            throw new ClientException(CommunityErrorCodeEnum.POST_CONTENT_EMPTY);
        }
        checkSensitiveWords(uid, title, content);
        if (newImages != null) {
            // 旧图行打删除标记（不是物理删）：objectKey 还留着，完善期清理 MinIO 对象要靠它
            postImageMapper.update(null, Wrappers.lambdaUpdate(CommunityPostImageDO.class)
                    .set(CommunityPostImageDO::getDelFlag, CommunityConstant.DEL_FLAG_DELETED)
                    .eq(CommunityPostImageDO::getPostId, postDO.getPostId()));
            for (int i = 0; i < newImages.size(); i++) {
                PostImageReqDTO image = newImages.get(i);
                CommunityPostImageDO imageDO = new CommunityPostImageDO();
                imageDO.setPostId(postDO.getPostId());
                imageDO.setUrl(image.getUrl());
                imageDO.setObjectKey(image.getObjectKey());
                imageDO.setSort(i);
                postImageMapper.insert(imageDO);
            }
            // 封面跟着第 1 张走；图被清空时封面也要真的清掉
            postDO.setCoverUrl(newImages.isEmpty() ? null : newImages.get(0).getUrl());
        }
        if (newVideos != null) {
            // 和图片一样：旧行软删（保留 objectKey 给清理任务），再按新顺序整份插回去
            postVideoMapper.update(null, Wrappers.lambdaUpdate(CommunityPostVideoDO.class)
                    .set(CommunityPostVideoDO::getDelFlag, CommunityConstant.DEL_FLAG_DELETED)
                    .eq(CommunityPostVideoDO::getPostId, postDO.getPostId()));
            for (int i = 0; i < newVideos.size(); i++) {
                PostVideoReqDTO videoItem = newVideos.get(i);
                CommunityPostVideoDO videoDO = new CommunityPostVideoDO();
                videoDO.setPostId(postDO.getPostId());
                videoDO.setUrl(videoItem.getUrl());
                videoDO.setObjectKey(videoItem.getObjectKey());
                videoDO.setSort(i);
                postVideoMapper.insert(videoDO);
            }
        }
        postDO.setTitle(title);
        postDO.setContent(content);
        // 用 update wrapper 显式 set，而不是 updateById：
        // updateById 默认忽略 null 字段，封面/标题要"清空成 null"时它不会写进去
        LambdaUpdateWrapper<CommunityPostDO> updateWrapper = Wrappers.lambdaUpdate(CommunityPostDO.class)
                .set(CommunityPostDO::getTitle, postDO.getTitle())
                .set(CommunityPostDO::getContent, postDO.getContent())
                .eq(CommunityPostDO::getPostId, postDO.getPostId())
                .eq(CommunityPostDO::getUid, uid);
        if (newImages != null) {
            updateWrapper.set(CommunityPostDO::getCoverUrl, postDO.getCoverUrl());
        }
        // update_time 由 MyMetaObjectHandler 自动填，详情里能看出"改过"
        baseMapper.update(null, updateWrapper);
        // 话题整份重建（顺序跟新正文走）；提及只对"原来没有、现在才有"的人发通知，
        // 否则改个错别字就把上次 @ 过的人再炸一遍
        Set<String> oldMentions = ContentTagParser.parseMentions(oldTitle, oldContent).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        topicService.replacePostTopics(postDO.getPostId(), title, content);
        Set<String> newMentions = new LinkedHashSet<>();
        for (String mention : ContentTagParser.parseMentions(title, content)) {
            if (!oldMentions.contains(mention.toLowerCase(Locale.ROOT))) {
                newMentions.add(mention);
            }
        }
        mentionService.notifyMentions(uid, UserContext.getUsername(), newMentions,
                postDO.getPostId(), postDO.getPostId(), StrUtil.blankToDefault(title, content));
        return buildDetail(postDO);
    }

    @Override
    public PageRespDTO<PostRespDTO> pageMyLikedPosts(Long pageNum, Long pageSize) {
        Long uid = currentLoginUid();
        long page = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);
        // 先分页点赞明细表（uid + create_time 索引），拿到本页的帖子 id
        IPage<CommunityLikeDO> likePage = likeMapper.selectPage(new Page<>(page, size),
                Wrappers.lambdaQuery(CommunityLikeDO.class)
                        .eq(CommunityLikeDO::getUid, uid)
                        .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                        .orderByDesc(CommunityLikeDO::getCreateTime)
                        .orderByDesc(CommunityLikeDO::getLikeId));
        List<Long> postIds = likePage.getRecords().stream()
                .map(CommunityLikeDO::getTargetId)
                .collect(Collectors.toList());
        return assembleMyPage(page, size, likePage.getTotal(), postIds, uid);
    }

    @Override
    public PageRespDTO<PostRespDTO> pageMyFavoritedPosts(Long pageNum, Long pageSize) {
        Long uid = currentLoginUid();
        long page = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);
        IPage<CommunityFavoriteDO> favoritePage = favoriteMapper.selectPage(new Page<>(page, size),
                Wrappers.lambdaQuery(CommunityFavoriteDO.class)
                        .eq(CommunityFavoriteDO::getUid, uid)
                        .eq(CommunityFavoriteDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                        .orderByDesc(CommunityFavoriteDO::getCreateTime)
                        .orderByDesc(CommunityFavoriteDO::getFavoriteId));
        List<Long> postIds = favoritePage.getRecords().stream()
                .map(CommunityFavoriteDO::getTargetId)
                .collect(Collectors.toList());
        return assembleMyPage(page, size, favoritePage.getTotal(), postIds, uid);
    }

    @Override
    public CursorPageRespDTO<PostRespDTO> pageUserPosts(Long uid, Long lastId, Long size) {
        Long targetUid = uid == null ? currentLoginUid() : uid;
        long limit = normalizePageSize(size);
        // 拉黑关系里看不到对方的主页内容：返回空流而不是报错，主页本身还能打开
        if (blockService.blockedBetween(currentUidOrNull(), targetUid)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        // 按 post_id 游标倒序：post_id 自增，等价于发布时间倒序，比 create_time 少一层排序键
        List<CommunityPostDO> rows = baseMapper.selectList(Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getUid, targetUid)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .lt(lastId != null, CommunityPostDO::getPostId, lastId)
                .orderByDesc(CommunityPostDO::getPostId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = rows.size() > limit;
        List<CommunityPostDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        if (CollUtil.isEmpty(pageList)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        List<PostRespDTO> records = toRespList(pageList, currentUidOrNull());
        Long nextCursor = hasMore ? records.get(records.size() - 1).getPostId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    @Override
    public CursorPageRespDTO<PostRespDTO> pageUserLikedPosts(Long uid, Long lastId, Long size) {
        Long targetUid = uid == null ? currentLoginUid() : uid;
        long limit = normalizePageSize(size);
        // 拉黑关系里连"他点赞过什么"也不展示
        if (blockService.blockedBetween(currentUidOrNull(), targetUid)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        // 先按 like_id 游标翻点赞明细表，拿到"他点赞过的帖子 id"（最近点赞的在最前）
        List<CommunityLikeDO> likeRows = likeMapper.selectList(Wrappers.lambdaQuery(CommunityLikeDO.class)
                .eq(CommunityLikeDO::getUid, targetUid)
                .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                .lt(lastId != null, CommunityLikeDO::getLikeId, lastId)
                .orderByDesc(CommunityLikeDO::getLikeId)
                .last("LIMIT " + (limit + 1)));
        boolean hasMore = likeRows.size() > limit;
        List<CommunityLikeDO> likePage = hasMore ? likeRows.subList(0, (int) limit) : likeRows;
        if (CollUtil.isEmpty(likePage)) {
            return CursorPageRespDTO.of(Collections.emptyList(), limit, false, null);
        }
        List<Long> postIds = likePage.stream().map(CommunityLikeDO::getTargetId).collect(Collectors.toList());
        // 帖子可能已被作者删除/下架，查不到的跳过，因此这一页条数可能少于 size
        Set<Long> invisibleUids = blockService.invisibleUids(currentUidOrNull());
        List<CommunityPostDO> postDOList = baseMapper.selectList(Wrappers.lambdaQuery(CommunityPostDO.class)
                .in(CommunityPostDO::getPostId, postIds)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .notIn(CollUtil.isNotEmpty(invisibleUids), CommunityPostDO::getUid, invisibleUids));
        Map<Long, CommunityPostDO> postMap = postDOList.stream()
                .collect(Collectors.toMap(CommunityPostDO::getPostId, postDO -> postDO, (existing, replacement) -> existing));
        List<CommunityPostDO> orderedList = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<PostRespDTO> records = toRespList(orderedList, currentUidOrNull());
        // 游标用点赞明细的 like_id，不是 postId：下次翻页要继续沿着"点赞时间"往下走
        Long nextCursor = hasMore ? likePage.get(likePage.size() - 1).getLikeId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    /**
     * 按"互动时间倒序"的帖子 id 顺序装配帖子卡片
     * <p>
     * 帖子可能已被作者删除或下架（查不到），这类记录直接跳过，
     * 所以本页条数可能少于 pageSize；total 统计的是互动记录数，两者会有轻微偏差（可接受）。
     */
    private PageRespDTO<PostRespDTO> assembleMyPage(long page, long size, long total, List<Long> postIds, Long uid) {
        if (CollUtil.isEmpty(postIds)) {
            return PageRespDTO.of(page, size, total, Collections.emptyList());
        }
        // 我自己的点赞/收藏列表同样过滤掉互相不可见的作者
        Set<Long> invisibleUids = blockService.invisibleUids(uid);
        List<CommunityPostDO> postDOList = baseMapper.selectList(Wrappers.lambdaQuery(CommunityPostDO.class)
                .in(CommunityPostDO::getPostId, postIds)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .notIn(CollUtil.isNotEmpty(invisibleUids), CommunityPostDO::getUid, invisibleUids));
        Map<Long, CommunityPostDO> postMap = postDOList.stream()
                .collect(Collectors.toMap(CommunityPostDO::getPostId, postDO -> postDO, (existing, replacement) -> existing));
        // 用 SQL 的 in 查出来是无序的，这里按互动时间顺序重新排一遍
        List<CommunityPostDO> orderedList = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PageRespDTO.of(page, size, total, toRespList(orderedList, uid));
    }

    @Override
    public CursorPageRespDTO<PostRespDTO> searchPosts(String keyword, Long sectionId, Long lastId, Long size) {
        String trimmed = StrUtil.trim(keyword);
        if (StrUtil.isBlank(trimmed) || trimmed.length() > CommunityConstant.SEARCH_KEYWORD_MAX_LENGTH) {
            throw new ClientException(CommunityErrorCodeEnum.SEARCH_KEYWORD_INVALID);
        }
        long limit = normalizePageSize(size);
        // 关键词先转义再进 LIKE：用户搜 "100%" 时不该把整张表捞出来
        String pattern = SqlLikeUtil.escape(trimmed);
        LambdaQueryWrapper<CommunityPostDO> queryWrapper = Wrappers.lambdaQuery(CommunityPostDO.class)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .and(wrapper -> wrapper.like(CommunityPostDO::getTitle, pattern)
                        .or().like(CommunityPostDO::getContent, pattern));
        if (sectionId != null) {
            queryWrapper.eq(CommunityPostDO::getSectionId, sectionId);
        }
        // 与我互相不可见的作者的帖子不进搜索结果（和帖子流一个口径）
        Set<Long> invisibleUids = blockService.invisibleUids(currentUidOrNull());
        queryWrapper.notIn(CollUtil.isNotEmpty(invisibleUids), CommunityPostDO::getUid, invisibleUids);
        // 排序按 post_id 倒序（最新在前）：相关度排序要等接 ES，
        // 现在先用自增主键保证游标稳定 —— 不重不漏比"看起来更相关"更重要
        queryWrapper.lt(lastId != null, CommunityPostDO::getPostId, lastId)
                .orderByDesc(CommunityPostDO::getPostId)
                .last("LIMIT " + (limit + 1));
        List<CommunityPostDO> rows = baseMapper.selectList(queryWrapper);
        boolean hasMore = rows.size() > limit;
        List<CommunityPostDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        List<PostRespDTO> records = toRespList(pageList, currentUidOrNull());
        Long nextCursor = hasMore && !records.isEmpty() ? records.get(records.size() - 1).getPostId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    @Override
    public CursorPageRespDTO<PostRespDTO> pagePostsByTopic(Long topicId, Long lastId, Long size) {
        // 话题不存在就别再查帖子了：前端拿错 id 时该看到"话题不存在"，而不是一个空列表
        topicService.getTopic(topicId);
        long limit = normalizePageSize(size);
        Set<Long> invisibleUids = blockService.invisibleUids(currentUidOrNull());
        // 用 inSql 交给数据库按 idx_topic_id 找关联帖：比"先把话题下所有 postId 捞进内存再 IN"更省内存，
        // 拼进去的是 Long 类型的 topicId，不存在注入
        LambdaQueryWrapper<CommunityPostDO> queryWrapper = Wrappers.lambdaQuery(CommunityPostDO.class)
                .inSql(CommunityPostDO::getPostId,
                        "SELECT post_id FROM miaowang_community_post_topic WHERE topic_id = " + topicId)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .notIn(CollUtil.isNotEmpty(invisibleUids), CommunityPostDO::getUid, invisibleUids)
                .lt(lastId != null, CommunityPostDO::getPostId, lastId)
                .orderByDesc(CommunityPostDO::getPostId)
                .last("LIMIT " + (limit + 1));
        List<CommunityPostDO> rows = baseMapper.selectList(queryWrapper);
        boolean hasMore = rows.size() > limit;
        List<CommunityPostDO> pageList = hasMore ? rows.subList(0, (int) limit) : rows;
        List<PostRespDTO> records = toRespList(pageList, currentUidOrNull());
        Long nextCursor = hasMore && !records.isEmpty() ? records.get(records.size() - 1).getPostId() : null;
        return CursorPageRespDTO.of(records, limit, hasMore, nextCursor);
    }

    /**
     * 组装帖子详情：帖子字段 + 图片明细 + 当前用户的点赞/收藏状态
     */
    private PostDetailRespDTO buildDetail(CommunityPostDO postDO) {
        PostDetailRespDTO respDTO = BeanUtil.toBean(postDO, PostDetailRespDTO.class);
        List<CommunityPostImageDO> imageDOList = postImageMapper.selectList(Wrappers.lambdaQuery(CommunityPostImageDO.class)
                .eq(CommunityPostImageDO::getPostId, postDO.getPostId())
                .eq(CommunityPostImageDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .orderByAsc(CommunityPostImageDO::getSort));
        respDTO.setImages(imageDOList.stream()
                .map(imageDO -> BeanUtil.toBean(imageDO, PostImageRespDTO.class))
                .collect(Collectors.toList()));
        Long uid = currentUidOrNull();
        respDTO.setLiked(uid != null && existsLike(uid, postDO.getPostId()));
        respDTO.setFavorited(uid != null && existsFavorite(uid, postDO.getPostId()));
        respDTO.setTopics(loadTopics(Collections.singletonList(postDO.getPostId()))
                .getOrDefault(postDO.getPostId(), Collections.emptyList()));
        respDTO.setVideos(loadVideos(Collections.singletonList(postDO.getPostId()))
                .getOrDefault(postDO.getPostId(), Collections.emptyList()));
        return respDTO;
    }

    /**
     * 组装帖子卡片列表：批量查一次点赞/收藏关系，避免每条帖子都查一次库（N+1）
     */
    private List<PostRespDTO> toRespList(List<CommunityPostDO> postDOList, Long uid) {
        if (CollUtil.isEmpty(postDOList)) {
            return Collections.emptyList();
        }
        Set<Long> likedIds = Collections.emptySet();
        Set<Long> favoritedIds = Collections.emptySet();
        if (uid != null) {
            List<Long> postIds = postDOList.stream().map(CommunityPostDO::getPostId).collect(Collectors.toList());
            likedIds = likeMapper.selectList(Wrappers.lambdaQuery(CommunityLikeDO.class)
                            .eq(CommunityLikeDO::getUid, uid)
                            .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                            .in(CommunityLikeDO::getTargetId, postIds))
                    .stream().map(CommunityLikeDO::getTargetId).collect(Collectors.toSet());
            favoritedIds = favoriteMapper.selectList(Wrappers.lambdaQuery(CommunityFavoriteDO.class)
                            .eq(CommunityFavoriteDO::getUid, uid)
                            .eq(CommunityFavoriteDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                            .in(CommunityFavoriteDO::getTargetId, postIds))
                    .stream().map(CommunityFavoriteDO::getTargetId).collect(Collectors.toSet());
        }
        // 话题一次批量查完（两条 SQL：关联表 + 话题表），不按帖子逐条查；
        // 视频同理：这一页的帖子一次查完，避免每条帖子查一次库（N+1）
        List<Long> pagePostIds = postDOList.stream().map(CommunityPostDO::getPostId).collect(Collectors.toList());
        Map<Long, List<TopicRespDTO>> topicMap = loadTopics(pagePostIds);
        Map<Long, List<PostVideoRespDTO>> videoMap = loadVideos(pagePostIds);
        List<PostRespDTO> respDTOList = new ArrayList<>(postDOList.size());
        for (CommunityPostDO postDO : postDOList) {
            PostRespDTO respDTO = BeanUtil.toBean(postDO, PostRespDTO.class);
            respDTO.setLiked(likedIds.contains(postDO.getPostId()));
            respDTO.setFavorited(favoritedIds.contains(postDO.getPostId()));
            respDTO.setTopics(topicMap.getOrDefault(postDO.getPostId(), Collections.emptyList()));
            respDTO.setVideos(videoMap.getOrDefault(postDO.getPostId(), Collections.emptyList()));
            respDTOList.add(respDTO);
        }
        return respDTOList;
    }

    /**
     * 批量取帖子的视频（一次查完一页，避免每条帖子查一次库）
     * <p>
     * Map 里没有 key 的帖子就是"没有视频"，调用方用 getOrDefault 兜空列表 ——
     * 返回给前端的一律是数组（没有视频就是空数组），前端不用判 null。
     */
    private Map<Long, List<PostVideoRespDTO>> loadVideos(List<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            return Collections.emptyMap();
        }
        List<CommunityPostVideoDO> videoDOList = postVideoMapper.selectList(
                Wrappers.lambdaQuery(CommunityPostVideoDO.class)
                        .eq(CommunityPostVideoDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                        .in(CommunityPostVideoDO::getPostId, postIds)
                        .orderByAsc(CommunityPostVideoDO::getSort));
        return videoDOList.stream()
                .collect(Collectors.groupingBy(CommunityPostVideoDO::getPostId,
                        Collectors.mapping(videoDO -> {
                            PostVideoRespDTO videoDTO = new PostVideoRespDTO();
                            videoDTO.setUrl(videoDO.getUrl());
                            videoDTO.setObjectKey(videoDO.getObjectKey());
                            return videoDTO;
                        }, Collectors.toList())));
    }

    /**
     * 批量取帖子的话题标签；查不到话题的帖子会缺 key，调用方用 getOrDefault 兜空列表
     */
    private Map<Long, List<TopicRespDTO>> loadTopics(List<Long> postIds) {
        return topicService.listTopicsByPostIds(postIds);
    }

    private boolean existsLike(Long uid, Long postId) {
        Long count = likeMapper.selectCount(Wrappers.lambdaQuery(CommunityLikeDO.class)
                .eq(CommunityLikeDO::getUid, uid)
                .eq(CommunityLikeDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                .eq(CommunityLikeDO::getTargetId, postId));
        return count != null && count > 0;
    }

    private boolean existsFavorite(Long uid, Long postId) {
        Long count = favoriteMapper.selectCount(Wrappers.lambdaQuery(CommunityFavoriteDO.class)
                .eq(CommunityFavoriteDO::getUid, uid)
                .eq(CommunityFavoriteDO::getTargetType, CommunityConstant.TARGET_TYPE_POST)
                .eq(CommunityFavoriteDO::getTargetId, postId));
        return count != null && count > 0;
    }

    /**
     * 敏感词校验：命中就拒绝写库
     * <p>
     * 命中的词只记日志、不回显给前端 —— 回显等于把词库送出去，改两个字就能绕过去。
     */
    private void checkSensitiveWords(Long uid, String title, String content) {
        StringBuilder text = new StringBuilder();
        if (title != null) {
            text.append(title);
        }
        if (content != null) {
            text.append('\n').append(content);
        }
        List<String> hits = sensitiveWordService.matches(text.toString());
        if (CollUtil.isNotEmpty(hits)) {
            log.warn("帖子内容命中敏感词, uid={}, hits={}", uid, hits);
            throw new ClientException(CommunityErrorCodeEnum.SENSITIVE_WORD);
        }
    }

    /**
     * 需要登录的操作统一从这里取 uid，取不到直接抛"未登录"
     */
    private Long currentLoginUid() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException(CommunityErrorCodeEnum.USER_NOT_LOGIN);
        }
        return Long.parseLong(userId);
    }

    /**
     * 读接口用这个：未登录返回 null（点赞/收藏状态一律 false），不报错
     */
    private Long currentUidOrNull() {
        String userId = UserContext.getUserId();
        return StrUtil.isBlank(userId) ? null : Long.parseLong(userId);
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