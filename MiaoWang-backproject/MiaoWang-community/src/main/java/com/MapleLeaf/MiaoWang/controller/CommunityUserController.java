package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetBriefRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserProfileRespDTO;
import com.MapleLeaf.MiaoWang.service.PostService;
import com.MapleLeaf.MiaoWang.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人主页接口：点昵称/头像进来看到的"这个人的一切"
 * <p>
 * 分成 4 个接口而不是一个大聚合：头部数字要得急（首屏），帖子流/点赞流是滚动加载的，
 * 宠物是跨服务调用（可能慢或不可用），绑成一个接口会让首屏等最慢的那一环。
 */
@RestController
@RequiredArgsConstructor
public class CommunityUserController {

    private final ProfileService profileService;
    private final PostService postService;

    /**
     * 主页头部：昵称、帖子数、获赞总数、关注数、粉丝数、是否已关注
     *
     * @param uid 不传=自己的主页（需要登录）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/user/profile")
    public Result<UserProfileRespDTO> getProfile(@RequestParam(value = "uid", required = false) Long uid) {
        return Results.success(profileService.getProfile(uid));
    }

    /**
     * 他发布的帖子（游标分页，最新在前）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/user/post")
    public Result<CursorPageRespDTO<PostRespDTO>> pageUserPosts(@RequestParam(value = "uid", required = false) Long uid,
                                                               @RequestParam(value = "lastId", required = false) Long lastId,
                                                               @RequestParam(value = "size", required = false) Long size) {
        return Results.success(postService.pageUserPosts(uid, lastId, size));
    }

    /**
     * 他点赞过的帖子（游标分页，最近点赞的在前）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/user/like")
    public Result<CursorPageRespDTO<PostRespDTO>> pageUserLikedPosts(@RequestParam(value = "uid", required = false) Long uid,
                                                                    @RequestParam(value = "lastId", required = false) Long lastId,
                                                                    @RequestParam(value = "size", required = false) Long size) {
        return Results.success(postService.pageUserLikedPosts(uid, lastId, size));
    }

    /**
     * 他的宠物（跨服务调宠物服务；宠物服务不可用时返回空列表，不影响主页其他区块）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/user/pet")
    public Result<List<PetBriefRespDTO>> listPets(@RequestParam(value = "uid", required = false) Long uid) {
        return Results.success(profileService.listPets(uid));
    }
}