package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子-话题关联
 * <p>
 * 用自增主键而不是联合主键：id 的插入顺序就是话题在正文里的出现顺序，
 * 详情/列表按 id 升序取出来，话题顺序就与作者写的一致。
 * <p>
 * 删除是物理删除（与点赞/收藏同一口径），uk_post_topic 防重复挂。
 */
@Data
@TableName("miaowang_community_post_topic")
@EqualsAndHashCode(callSuper = false)
public class CommunityPostTopicDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 帖子 id
     */
    private Long postId;

    /**
     * 话题 id
     */
    private Long topicId;
}