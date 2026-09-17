package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.BlockUserRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;

import java.util.Set;

/**
 * 社区拉黑
 * <p>
 * 语义是**互相不可见**：拉黑之后双方的帖子与评论都从对方视野里消失。
 * 只做单向隐藏等于没拉黑 —— 被拉黑的人照样能跑到你的帖子下面评论。
 */
public interface BlockService {

    /**
     * 拉黑某人（幂等）
     * <p>
     * 同时解除双向关注、清掉双向通知：拉黑之后不该再在关注列表/通知列表里看见对方。
     */
    void block(Long blockUid);

    /**
     * 取消拉黑（幂等，不恢复原来已经被解除的关注关系）
     */
    void unblock(Long blockUid);

    /**
     * 我的黑名单（游标分页，最近拉黑的在前）
     *
     * @param lastId 上一页的 nextCursor
     * @param size   每页条数
     */
    CursorPageRespDTO<BlockUserRespDTO> pageBlocked(Long lastId, Long size);

    /**
     * 与 uid 互相不可见的用户集合（我拉黑的人 + 拉黑我的人）
     * <p>
     * 列表查询用这一个集合过滤即可；uid 为空（未登录）返回空集合。
     */
    Set<Long> invisibleUids(Long uid);

    /**
     * 两人之间是否存在拉黑关系（任一方向）
     * <p>
     * 互动与通知写入前用它做最后一道拦截：内容列表已经过滤了，
     * 但接口是可以被直接调用的，所以写操作要再查一次。
     */
    boolean blockedBetween(Long oneUid, Long anotherUid);
}