package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.CommentCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CommentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.service.CommentService;
import com.MapleLeaf.MiaoWang.service.InteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区评论接口
 * <p>
 * 列表用游标分页（前端滚动加载）：请求带 size，响应给 hasMore + nextCursor，
 * 下一页把 nextCursor 原样回传给 lastId。没有页码，也没有 total。
 */
@RestController
@RequiredArgsConstructor
public class CommunityCommentController {

    private final CommentService commentService;
    private final InteractionService interactionService;

    /**
     * 发表评论 / 回复
     * <p>
     * 评论帖子：只传 postId + content；
     * 回复一级评论：再传 parentId；
     * 回复二级评论：传 parentId（该二级评论 id）+ replyUid（被回复的人）。
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/comment")
    public Result<CommentRespDTO> createComment(@RequestBody CommentCreateReqDTO requestParam) {
        return Results.success(commentService.createComment(requestParam));
    }

    /**
     * 一级评论的滚动加载（游标分页）
     * <p>
     * 每条一级评论带最多 3 条回复预览 + replyCount（回复总数）。
     *
     * @param postId 帖子 id
     * @param lastId 上一页返回的 nextCursor；第一页不传
     * @param size   每页条数，默认 10，最大 50
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/comment")
    public Result<CursorPageRespDTO<CommentRespDTO>> pageComments(@RequestParam(required = false) Long postId,
                                                                  @RequestParam(required = false) Long lastId,
                                                                  @RequestParam(required = false) Long size) {
        return Results.success(commentService.pageComments(postId, lastId, size));
    }

    /**
     * 查看某条一级评论下的全部回复（游标分页，按时间正序）
     * <p>
     * 用户点"查看全部 N 条回复"时调这个接口。
     *
     * @param commentId 一级评论 id
     * @param lastId    上一页返回的 nextCursor；第一页不传
     * @param size      每页条数，默认 10，最大 50
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/comment/reply")
    public Result<CursorPageRespDTO<CommentRespDTO>> pageReplies(@RequestParam(required = false) Long commentId,
                                                                 @RequestParam(required = false) Long lastId,
                                                                 @RequestParam(required = false) Long size) {
        return Results.success(commentService.pageReplies(commentId, lastId, size));
    }

    /**
     * 删除自己的评论（物理删除，不入回收站；删一级评论会连同它下面的回复一起删）
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/community/comment/{commentId}")
    public Result<Void> deleteComment(@PathVariable("commentId") Long commentId) {
        commentService.deleteComment(commentId);
        return Results.success();
    }

    /**
     * 点赞评论
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/comment/like/{commentId}")
    public Result<Void> likeComment(@PathVariable("commentId") Long commentId) {
        interactionService.likeComment(commentId);
        return Results.success();
    }

    /**
     * 取消点赞评论
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/community/comment/like/{commentId}")
    public Result<Void> unlikeComment(@PathVariable("commentId") Long commentId) {
        interactionService.unlikeComment(commentId);
        return Results.success();
    }
}