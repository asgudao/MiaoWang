package com.MapleLeaf.MiaoWang.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 社区通知（谁赞了你 / 谁评论了你 / 谁回复了你 / 谁关注了你）
 * <p>
 * 去重靠唯一键 uk_receiver_type_target_actor：同一个人对同一个目标只留一条记录，
 * 重复触发是"刷新"而不是"新增"，否则取消赞再赞、取关再关注会把通知列表刷满。
 * <p>
 * 不继承 BaseDO：通知没有逻辑删除语义，删了就没了，不需要 del_flag。
 */
@Data
@TableName("miaowang_community_notification")
public class CommunityNotificationDO {

    /**
     * 通知id
     */
    @TableId(type = IdType.AUTO)
    private Long notificationId;

    /**
     * 接收者uid
     */
    private Long receiverUid;

    /**
     * 通知类型：1点赞帖子 2点赞评论 3评论帖子 4回复评论 5关注
     */
    private Integer type;

    /**
     * 触发者uid
     */
    private Long actorUid;

    /**
     * 触发者昵称（冗余一份，省得每条通知都回查用户服务）
     */
    private String actorUsername;

    /**
     * 目标id：帖子id / 评论id；关注类通知为 0
     */
    private Long targetId;

    /**
     * 所属帖子id，前端点击跳转用；关注类通知为 0
     */
    private Long postId;

    /**
     * 内容摘要快照（按触发时的内容截断保存，不随原内容变化）
     */
    private String content;

    /**
     * 0未读 1已读
     */
    private Integer readFlag;

    /**
     * 通知时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}