package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.req.CommentCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CommentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;

/**
 * 社区评论
 */
public interface CommentService {

    /**
     * 发表评论或回复（两级：一级评论 + 其下回复）
     */
    CommentRespDTO createComment(CommentCreateReqDTO requestParam);

    /**
     * 一级评论的滚动加载（游标分页，不是页码）
     * <p>
     * 每条一级评论只带前几条回复预览 + 回复总数，想看全部回复走 {@link #pageReplies}。
     *
     * @param postId 帖子 id
     * @param lastId 上一页返回的 nextCursor；传 null 表示取第一页（最新的）
     * @param size   每页条数
     */
    CursorPageRespDTO<CommentRespDTO> pageComments(Long postId, Long lastId, Long size);

    /**
     * 某条一级评论下的全部回复（游标分页，按时间正序）
     *
     * @param commentId 一级评论 id
     * @param lastId    上一页返回的 nextCursor；传 null 表示取第一页（最早的）
     * @param size      每页条数
     */
    CursorPageRespDTO<CommentRespDTO> pageReplies(Long commentId, Long lastId, Long size);

    /**
     * 删除自己的评论：物理删除（不做逻辑删除）
     * <p>
     * 删一级评论时，连同它下面的回复、以及这些评论的点赞关系一起删。
     */
    void deleteComment(Long commentId);
}