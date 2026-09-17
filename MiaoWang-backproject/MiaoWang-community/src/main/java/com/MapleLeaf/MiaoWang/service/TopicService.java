package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.TopicRespDTO;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 社区话题
 * <p>
 * 话题没有"先建后发"的流程，全部由正文解析而来（见
 * {@link com.MapleLeaf.MiaoWang.support.ContentTagParser}）：发帖时按名字找话题，
 * 找不到就顺手建一个。
 * <p>
 * post_count 是冗余计数，只在本类里成对维护（+1 / -1），其它地方不要动它。
 */
public interface TopicService {

    /**
     * 发帖时挂话题：已存在的话题复用，同一帖重复挂同一话题会被唯一键挡掉（计数也不会重复加）
     */
    void syncPostTopics(Long postId, String title, String content);

    /**
     * 编辑帖子时重建话题关联
     * <p>
     * 整份删掉再按新正文顺序重建：话题在正文里的先后顺序就是详情里的顺序，
     * 只做增删差量的话，编辑后顺序会和正文对不上。计数只按"新增 / 移除"动差量，
     * 还在的话题计数不动（否则编辑一次热门榜就抖一次）。
     */
    void replacePostTopics(Long postId, String title, String content);

    /**
     * 删帖时摘掉话题关联，并把对应话题的 post_count -1
     */
    void removePostTopics(Long postId);

    /**
     * 批量取一组帖子的话题（列表页一次查完，不做逐帖查询）
     *
     * @return postId → 话题列表（按正文出现顺序）；没有话题的帖子不会出现在 map 里
     */
    Map<Long, List<TopicRespDTO>> listTopicsByPostIds(Collection<Long> postIds);

    /**
     * 热门话题（按关联帖子数倒序，是榜单不是流水，不做分页）
     */
    List<TopicRespDTO> hotTopics(Long size);

    /**
     * 按名字前缀搜话题（前缀匹配能用上 uk_name 索引，不做 %kw% 全表扫）
     */
    List<TopicRespDTO> searchTopics(String keyword, Long size);

    /**
     * 话题详情；不存在抛 {@code B000326}
     */
    TopicRespDTO getTopic(Long topicId);
}