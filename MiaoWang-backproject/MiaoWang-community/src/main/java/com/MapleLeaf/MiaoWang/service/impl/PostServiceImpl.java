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
import com.MapleLeaf.MiaoWang.dao.entity.CommunitySectionDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityFavoriteMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityLikeMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostImageMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunityPostMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunitySectionMapper;
import com.MapleLeaf.MiaoWang.dto.req.PostCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PostImageReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostDetailRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostImageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostRespDTO;
import com.MapleLeaf.MiaoWang.enums.CommunityErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.PostService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
@Service
@RequiredArgsConstructor
public class PostServiceImpl extends ServiceImpl<CommunityPostMapper, CommunityPostDO> implements PostService {

    private final CommunityPostImageMapper postImageMapper;
    private final CommunitySectionMapper sectionMapper;
    private final CommunityLikeMapper likeMapper;
    private final CommunityFavoriteMapper favoriteMapper;

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
        if (images.isEmpty() && StrUtil.isBlank(requestParam.getTitle()) && StrUtil.isBlank(requestParam.getContent())) {
            throw new ClientException(CommunityErrorCodeEnum.POST_CONTENT_EMPTY);
        }
        CommunityPostDO postDO = new CommunityPostDO();
        postDO.setSectionId(sectionDO.getSectionId());
        postDO.setUid(uid);
        postDO.setUsername(username);
        postDO.setTitle(StrUtil.trim(requestParam.getTitle()));
        postDO.setContent(requestParam.getContent());
        // 第 1 张图当封面，冗余到帖子表：列表页免联图片表
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
        List<CommunityPostDO> postDOList = baseMapper.selectList(Wrappers.lambdaQuery(CommunityPostDO.class)
                .in(CommunityPostDO::getPostId, postIds)
                .eq(CommunityPostDO::getStatus, CommunityConstant.POST_STATUS_PUBLISHED)
                .eq(CommunityPostDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL));
        Map<Long, CommunityPostDO> postMap = postDOList.stream()
                .collect(Collectors.toMap(CommunityPostDO::getPostId, postDO -> postDO, (existing, replacement) -> existing));
        // 用 SQL 的 in 查出来是无序的，这里按互动时间顺序重新排一遍
        List<CommunityPostDO> orderedList = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return PageRespDTO.of(page, size, total, toRespList(orderedList, uid));
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
        List<PostRespDTO> respDTOList = new ArrayList<>(postDOList.size());
        for (CommunityPostDO postDO : postDOList) {
            PostRespDTO respDTO = BeanUtil.toBean(postDO, PostRespDTO.class);
            respDTO.setLiked(likedIds.contains(postDO.getPostId()));
            respDTO.setFavorited(favoritedIds.contains(postDO.getPostId()));
            respDTOList.add(respDTO);
        }
        return respDTOList;
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