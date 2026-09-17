package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.req.PostCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PostUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
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
     * 编辑自己的帖子（仅作者；版块不允许改）
     * <p>
     * 字段语义见 {@link com.MapleLeaf.MiaoWang.dto.req.PostUpdateReqDTO}：
     * title/content 传 null 表示不改，images 传 null 表示不动图片。
     * 编辑不留历史版本，改完就是改完了。
     */
    PostDetailRespDTO updatePost(Long postId, PostUpdateReqDTO requestParam);

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
     * 搜索帖子（标题或正文包含关键词，游标分页，最新在前）
     * <p>
     * 现在走 LIKE：中文没有词边界，LIKE '%kw%' 用不上索引，量级上来要接 ES
     * （查询条件都收在这个方法里，换实现不用动上层）。关键词里的 % / _ 会先转义。
     *
     * @param keyword   关键词，空或超过 50 字抛 {@code B000327}
     * @param sectionId 版块 id，不传查全部版块
     */
    CursorPageRespDTO<PostRespDTO> searchPosts(String keyword, Long sectionId, Long lastId, Long size);

    /**
     * 某个话题下的帖子（游标分页，最新在前）；话题不存在抛 {@code B000326}
     */
    CursorPageRespDTO<PostRespDTO> pagePostsByTopic(Long topicId, Long lastId, Long size);

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

    /**
     * 某个用户发布的帖子（个人主页用，游标分页，最新在前）
     *
     * @param uid    用户id；传 null = 当前登录用户
     * @param lastId 游标（上一页最后一条的 postId）
     * @param size   每页条数
     */
    CursorPageRespDTO<PostRespDTO> pageUserPosts(Long uid, Long lastId, Long size);

    /**
     * 某个用户点赞过的帖子（个人主页用，游标分页，最近点赞的在前）
     *
     * @param uid    用户id；传 null = 当前登录用户
     * @param lastId 游标（上一页最后一条的 likeId）
     * @param size   每页条数
     */
    CursorPageRespDTO<PostRespDTO> pageUserLikedPosts(Long uid, Long lastId, Long size);
}