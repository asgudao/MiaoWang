package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.PostCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PostUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostDetailRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostRespDTO;
import com.MapleLeaf.MiaoWang.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区帖子接口
 * <p>
 * 发帖流程（前端）：先 POST /community/file/image 逐张上传拿到 url + objectKey，
 * 再把它们放进 images 提交到 POST /community/post。
 */
@RestController
@RequiredArgsConstructor
public class CommunityPostController {

    private final PostService postService;

    /**
     * 发布帖子（最多 9 张图）
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/post")
    public Result<PostDetailRespDTO> createPost(@RequestBody PostCreateReqDTO requestParam) {
        return Results.success(postService.createPost(requestParam));
    }

    /**
     * 编辑自己的帖子（仅作者；版块不可改，也不留历史版本）
     * <p>
     * title/content 不传 = 不改；images 不传 = 不动图片，传数组（含空数组）= 整份替换。
     */
    @PutMapping("/mapleleaf/miaowang/v1/community/post/{postId}")
    public Result<PostDetailRespDTO> updatePost(@PathVariable("postId") Long postId,
                                                @RequestBody PostUpdateReqDTO requestParam) {
        return Results.success(postService.updatePost(postId, requestParam));
    }

    /**
     * 帖子列表（可按版块过滤，分页）
     *
     * @param sectionId 版块 id，不传查全部版块
     * @param pageNum   页码，默认 1
     * @param pageSize  每页条数，默认 10，最大 50
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/post")
    public Result<PageRespDTO<PostRespDTO>> pagePosts(@RequestParam(required = false) Long sectionId,
                                                      @RequestParam(required = false) Long pageNum,
                                                      @RequestParam(required = false) Long pageSize) {
        return Results.success(postService.pagePosts(sectionId, pageNum, pageSize));
    }

    /**
     * 帖子详情（浏览数 +1）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/post/{postId}")
    public Result<PostDetailRespDTO> getPostDetail(@PathVariable("postId") Long postId) {
        return Results.success(postService.getPostDetail(postId));
    }

    /**
     * 搜索帖子（标题或正文包含关键词，游标分页）
     *
     * @param keyword   关键词（空或超过 50 字 → B000327）
     * @param sectionId 版块 id，不传搜全部版块
     * @param lastId    游标（上一页最后一条的 postId）
     * @param size      每页条数，默认 10，最大 50
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/post/search")
    public Result<CursorPageRespDTO<PostRespDTO>> searchPosts(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Long sectionId,
                                                              @RequestParam(required = false) Long lastId,
                                                              @RequestParam(required = false) Long size) {
        return Results.success(postService.searchPosts(keyword, sectionId, lastId, size));
    }

    /**
     * 删除自己的帖子（逻辑删除）
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/community/post/{postId}")
    public Result<Void> deletePost(@PathVariable("postId") Long postId) {
        postService.deletePost(postId);
        return Results.success();
    }

    /**
     * 我点赞过的帖子
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/my/like")
    public Result<PageRespDTO<PostRespDTO>> pageMyLikedPosts(@RequestParam(required = false) Long pageNum,
                                                             @RequestParam(required = false) Long pageSize) {
        return Results.success(postService.pageMyLikedPosts(pageNum, pageSize));
    }

    /**
     * 我收藏过的帖子
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/my/favorite")
    public Result<PageRespDTO<PostRespDTO>> pageMyFavoritedPosts(@RequestParam(required = false) Long pageNum,
                                                                 @RequestParam(required = false) Long pageSize) {
        return Results.success(postService.pageMyFavoritedPosts(pageNum, pageSize));
    }
}