package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.service.InteractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区互动接口：点赞、收藏
 * <p>
 * 点赞/取消点赞用 POST/DELETE 同一个路径，语义清晰；
 * 两个动作都幂等：重复点赞不报错，取消一个没点过的赞也不报错。
 */
@RestController
@RequiredArgsConstructor
public class CommunityInteractionController {

    private final InteractionService interactionService;

    /**
     * 点赞帖子
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/like/{postId}")
    public Result<Void> likePost(@PathVariable("postId") Long postId) {
        interactionService.likePost(postId);
        return Results.success();
    }

    /**
     * 取消点赞
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/community/like/{postId}")
    public Result<Void> unlikePost(@PathVariable("postId") Long postId) {
        interactionService.unlikePost(postId);
        return Results.success();
    }

    /**
     * 收藏帖子
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/favorite/{postId}")
    public Result<Void> favoritePost(@PathVariable("postId") Long postId) {
        interactionService.favoritePost(postId);
        return Results.success();
    }

    /**
     * 取消收藏
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/community/favorite/{postId}")
    public Result<Void> unfavoritePost(@PathVariable("postId") Long postId) {
        interactionService.unfavoritePost(postId);
        return Results.success();
    }
}