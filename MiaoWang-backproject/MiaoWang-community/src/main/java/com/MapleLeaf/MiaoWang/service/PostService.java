package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.req.PostCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostDetailRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostRespDTO;

/**
 * 社区帖子
 */
public interface PostService {

    /**
     * 发布帖子（最多 9 张图，图片先走上传接口拿到 url/objectKey）
     */
    PostDetailRespDTO createPost(PostCreateReqDTO requestParam);

    /**
     * 帖子列表（按版块，可按发布时间倒序分页；置顶优先）
     *
     * @param sectionId 版块 id，不传则查全部版块
     */
    PageRespDTO<PostRespDTO> pagePosts(Long sectionId, Long pageNum, Long pageSize);

    /**
     * 帖子详情（浏览数 +1）
     */
    PostDetailRespDTO getPostDetail(Long postId);

    /**
     * 删除自己的帖子（逻辑删除）
     */
    void deletePost(Long postId);

    /**
     * 我点赞过的帖子（按点赞时间倒序）
     */
    PageRespDTO<PostRespDTO> pageMyLikedPosts(Long pageNum, Long pageSize);

    /**
     * 我收藏过的帖子（按收藏时间倒序）
     */
    PageRespDTO<PostRespDTO> pageMyFavoritedPosts(Long pageNum, Long pageSize);
}