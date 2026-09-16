package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.CommentCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CommentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
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
     * 评论分页列表（一级评论分页，每条带出它下面的回复）
     *
     * @param postId   帖子 id
     * @param pageNum  页码，默认 1
     * @param pageSize 每页条数，默认 10，最大 50
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/comment")
    public Result<PageRespDTO<CommentRespDTO>> pageComments(@RequestParam(required = false) Long postId,
                                                            @RequestParam(required = false) Long pageNum,
                                                            @RequestParam(required = false) Long pageSize) {
        return Results.success(commentService.pageComments(postId, pageNum, pageSize));
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