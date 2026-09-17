package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.BlockUserRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区拉黑接口
 * <p>
 * 黑名单列表同样是游标分页（records + hasMore + nextCursor，没有 total）。
 */
@RestController
@RequiredArgsConstructor
public class CommunityBlockController {

    private final BlockService blockService;

    /**
     * 拉黑某人（幂等；会同时解除双向关注、清掉双方通知）
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/block/{uid}")
    public Result<Void> block(@PathVariable("uid") Long uid) {
        blockService.block(uid);
        return Results.success();
    }

    /**
     * 取消拉黑（幂等；不恢复已解除的关注关系）
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/community/block/{uid}")
    public Result<Void> unblock(@PathVariable("uid") Long uid) {
        blockService.unblock(uid);
        return Results.success();
    }

    /**
     * 我的黑名单（只查自己的，不开放查别人的黑名单）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/block")
    public Result<CursorPageRespDTO<BlockUserRespDTO>> pageBlocked(@RequestParam(value = "lastId", required = false) Long lastId,
                                                                   @RequestParam(value = "size", required = false) Long size) {
        return Results.success(blockService.pageBlocked(lastId, size));
    }
}