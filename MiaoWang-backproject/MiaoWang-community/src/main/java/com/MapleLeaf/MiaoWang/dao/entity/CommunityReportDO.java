package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 社区举报记录
 * <p>
 * 举报只落一条"待处理"的记录，不直接改内容状态：
 * 判定违规要下架帖子/删评论，那是运营动作，得先有管理员角色体系才敢自动做。
 */
@Data
@TableName("miaowang_community_report")
@EqualsAndHashCode(callSuper = false)
public class CommunityReportDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long reportId;

    /**
     * 举报人 uid
     */
    private Long uid;

    /**
     * 举报人用户名（冗余，审核端不用再回查用户表）
     */
    private String username;

    /**
     * 目标类型：1帖子 2评论（复用社区的 TARGET_TYPE_*）
     */
    private Integer targetType;

    /**
     * 目标 id（帖子 id 或评论 id）
     */
    private Long targetId;

    /**
     * 所属帖子 id：举报帖子时=targetId，举报评论时=评论所在帖子
     * <p>
     * 存下来审核端才能一键跳到上下文，不用再按目标类型分两条路去查。
     */
    private Long postId;

    /**
     * 被举报内容的作者 uid（审核端按人聚合"谁老被举报"用）
     */
    private Long targetUid;

    /**
     * 举报理由分类：1广告引流 2辱骂攻击 3色情低俗 4违法违规 5虚假信息 6其他
     */
    private Integer reasonType;

    /**
     * 补充说明
     */
    private String reason;

    /**
     * 处理状态：0待处理 1已处理(违规) 2已驳回(未违规)
     */
    private Integer status;

    /**
     * 处理人 uid（0=还没人处理）
     */
    private Long handleUid;

    /**
     * 处理时间
     */
    private Date handleTime;
}