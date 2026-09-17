package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.FollowUserRespDTO;

/**
 * 关注服务
 */
public interface FollowService {

    /**
     * 关注某人（幂等：已关注时直接返回成功，不重复产生通知）
     *
     * @param followUid 被关注者uid
     */
    void follow(Long followUid);

    /**
     * 取消关注（物理删除，幂等：没关注过也返回成功）
     *
     * @param followUid 被关注者uid
     */
    void unfollow(Long followUid);

    /**
     * 关注列表：某人关注了谁
     *
     * @param uid      要查的用户；传 null = 查当前登录用户
     * @param lastId   游标（上一页最后一条的 followId）
     * @param size     每页条数
     * @return 游标分页结果
     */
    CursorPageRespDTO<FollowUserRespDTO> pageFollowing(Long uid, Long lastId, Long size);

    /**
     * 粉丝列表：谁关注了某人
     *
     * @param uid    要查的用户；传 null = 查当前登录用户
     * @param lastId 游标（上一页最后一条的 followId）
     * @param size   每页条数
     * @return 游标分页结果
     */
    CursorPageRespDTO<FollowUserRespDTO> pageFollower(Long uid, Long lastId, Long size);

    /**
     * 判断 uid 是否关注了 followUid（个人主页的"已关注"按钮状态）
     */
    boolean isFollowing(Long uid, Long followUid);

    /**
     * 某人关注了多少人
     */
    long countFollowing(Long uid);

    /**
     * 某人有多少粉丝
     */
    long countFollower(Long uid);
}