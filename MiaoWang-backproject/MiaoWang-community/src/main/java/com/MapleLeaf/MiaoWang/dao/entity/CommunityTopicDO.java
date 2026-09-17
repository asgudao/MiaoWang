package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区话题
 * <p>
 * 话题完全由帖子正文里的 #话题 解析出来（没有"先建话题再发帖"的流程）：
 * 先按名字找到话题，找不到就顺手建一个，然后在 miaowang_community_post_topic 里落关联。
 */
@Data
@TableName("miaowang_community_topic")
@EqualsAndHashCode(callSuper = false)
public class CommunityTopicDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long topicId;

    /**
     * 话题名（不含 #，最长 20 字符）
     */
    private String name;

    /**
     * 关联帖子数
     * <p>
     * 冗余计数：热门榜直接按它排序，不用去 COUNT 关联表。
     * 只允许 TopicService 成对维护（+1 / -1），别的地方不要动。
     */
    private Integer postCount;
}