package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.CursorPageRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PostRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.TopicRespDTO;
import com.MapleLeaf.MiaoWang.service.PostService;
import com.MapleLeaf.MiaoWang.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区话题接口（发现效率）
 * <p>
 * 话题由帖子正文里的 #话题 解析产生，这里只做"发现"：热门榜、搜话题、话题详情、话题下的帖子流。
 * 都是公开读接口，未登录也能看（点赞/收藏状态恒为 false）。
 */
@RestController
@RequiredArgsConstructor
public class CommunityTopicController {

    private final TopicService topicService;
    private final PostService postService;

    /**
     * 热门话题（按时段内关联帖子数倒序）
     * <p>
     * 是榜单不是流水，所以只给 size 不给游标：前端只展示前 N 个
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/topic/hot")
    public Result<List<TopicRespDTO>> hotTopics(@RequestParam(required = false) Long size) {
        return Results.success(topicService.hotTopics(size));
    }

    /**
     * 搜话题（名字前缀匹配）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/topic/search")
    public Result<List<TopicRespDTO>> searchTopics(@RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long size) {
        return Results.success(topicService.searchTopics(keyword, size));
    }

    /**
     * 话题详情
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/topic/{topicId}")
    public Result<TopicRespDTO> getTopic(@PathVariable("topicId") Long topicId) {
        return Results.success(topicService.getTopic(topicId));
    }

    /**
     * 话题下的帖子流（游标分页，最新在前）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/topic/{topicId}/post")
    public Result<CursorPageRespDTO<PostRespDTO>> pageTopicPosts(@PathVariable("topicId") Long topicId,
                                                                  @RequestParam(required = false) Long lastId,
                                                                  @RequestParam(required = false) Long size) {
        return Results.success(postService.pagePostsByTopic(topicId, lastId, size));
    }
}