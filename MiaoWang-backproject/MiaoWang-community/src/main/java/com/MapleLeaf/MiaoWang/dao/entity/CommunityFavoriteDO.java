package com.MapleLeaf.MiaoWang.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 社区收藏（多态归属：帖子 / 评论）
 * <p>
 * 收藏是"用户资产"，必须持久化在 MySQL（清 Redis 不能把用户收藏清掉）；
 * 同样不继承 BaseDO，取消收藏即物理删除。
 */
@Data
@TableName("miaowang_community_favorite")
public class CommunityFavoriteDO {

    @TableId(type = IdType.AUTO)
    private Long favoriteId;

    /**
     * 收藏用户 id
     */
    private Long uid;

    /**
     * 目标类型：1帖子 2评论
     */
    private Integer targetType;

    /**
     * 目标 id
     */
    private Long targetId;

    /**
     * 收藏时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}