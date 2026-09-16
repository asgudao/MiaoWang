package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.req.CommentCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CommentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;

/**
 * 社区评论
 */
public interface CommentService {

    /**
     * 发表评论或回复（两级：一级评论 + 其下回复）
     */
    CommentRespDTO createComment(CommentCreateReqDTO requestParam);

    /**
     * 评论分页列表：按一级评论分页，每条一级评论带出它下面的回复
     *
     * @param postId 帖子 id
     */
    PageRespDTO<CommentRespDTO> pageComments(Long postId, Long pageNum, Long pageSize);

    /**
     * 删除自己的评论：物理删除（不做逻辑删除）
     * <p>
     * 删一级评论时，连同它下面的回复、以及这些评论的点赞关系一起删。
     */
    void deleteComment(Long commentId);
}