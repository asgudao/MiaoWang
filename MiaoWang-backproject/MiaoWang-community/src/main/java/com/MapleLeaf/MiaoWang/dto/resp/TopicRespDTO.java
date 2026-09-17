package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 话题（热门榜 / 话题搜索 / 话题详情 / 帖子上的标签都用它）
 */
@Data
public class TopicRespDTO {

    private Long topicId;

    /**
     * 话题名（不含 #）
     */
    private String name;

    /**
     * 关联帖子数
     */
    private Integer postCount;
}