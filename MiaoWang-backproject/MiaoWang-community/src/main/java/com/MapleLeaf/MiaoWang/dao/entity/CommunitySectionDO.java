package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区版块（宠物美照 / 宠物知识 / 幼宠专区 / 品种鉴定 / 病症鉴定 / 活动专区）
 */
@Data
@TableName("miaowang_community_section")
@EqualsAndHashCode(callSuper = false)
public class CommunitySectionDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long sectionId;

    /**
     * 版块名称
     */
    private String name;

    /**
     * 版块编码：photo/knowledge/baby/breed/symptom/activity
     */
    private String code;

    /**
     * 版块简介
     */
    private String description;

    /**
     * 版块图标 URL
     */
    private String iconUrl;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;

    /**
     * 状态：0禁用 1启用
     */
    private Integer status;
}