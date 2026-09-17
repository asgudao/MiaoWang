package com.MapleLeaf.MiaoWang.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 社区关注关系
 * <p>
 * 一条记录 = "uid 关注了 follow_uid"。关注/取关是物理删除（和点赞一致），不留 del_flag 残行；
 * 重复关注靠唯一键 uk_uid_follow 兜底，不靠先查后写。
 */
@Data
@TableName("miaowang_community_follow")
public class CommunityFollowDO {

    /**
     * 关注id
     */
    @TableId(type = IdType.AUTO)
    private Long followId;

    /**
     * 关注者uid（"我"）
     */
    private Long uid;

    /**
     * 被关注者uid
     */
    private Long followUid;

    /**
     * 关注时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}