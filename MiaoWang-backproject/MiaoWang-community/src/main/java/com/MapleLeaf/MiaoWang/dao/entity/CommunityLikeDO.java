package com.MapleLeaf.MiaoWang.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 社区点赞（多态归属：帖子 / 评论）
 * <p>
 * 不继承 BaseDO：取消点赞即物理删除，所以表里没有 del_flag / update_time，
 * 避免"逻辑删除 + 唯一键"互相打架（软删后重新点赞会撞唯一键）。
 */
@Data
@TableName("miaowang_community_like")
public class CommunityLikeDO {

    @TableId(type = IdType.AUTO)
    private Long likeId;

    /**
     * 点赞用户 id
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
     * 点赞时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}