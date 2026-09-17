package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.FollowUserRespDTO;
import com.MapleLeaf.MiaoWang.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区关注 / 粉丝接口
 * <p>
 * 列表统一游标分页：返回 records + hasMore + nextCursor（没有 total），
 * 前端滚到底就把 nextCursor 原样当成 lastId 传回来。
 */
@RestController
@RequiredArgsConstructor
public class CommunityFollowController {

    private final FollowService followService;

    /**
     * 关注某人（幂等）
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/follow/{uid}")
    public Result<Void> follow(@PathVariable("uid") Long uid) {
        followService.follow(uid);
        return Results.success();
    }

    /**
     * 取消关注（幂等）
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/community/follow/{uid}")
    public Result<Void> unfollow(@PathVariable("uid") Long uid) {
        followService.unfollow(uid);
        return Results.success();
    }

    /**
     * 关注列表：uid 不传=我关注的人；传了=某人的关注列表（看别人主页时用，不需要登录）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/follow/following")
    public Result<CursorPageRespDTO<FollowUserRespDTO>> pageFollowing(@RequestParam(value = "uid", required = false) Long uid,
                                                                     @RequestParam(value = "lastId", required = false) Long lastId,
                                                                     @RequestParam(value = "size", required = false) Long size) {
        return Results.success(followService.pageFollowing(uid, lastId, size));
    }

    /**
     * 粉丝列表：uid 不传=我的粉丝；传了=某人的粉丝
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/follow/follower")
    public Result<CursorPageRespDTO<FollowUserRespDTO>> pageFollower(@RequestParam(value = "uid", required = false) Long uid,
                                                                    @RequestParam(value = "lastId", required = false) Long lastId,
                                                                    @RequestParam(value = "size", required = false) Long size) {
        return Results.success(followService.pageFollower(uid, lastId, size));
    }
}