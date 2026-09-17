package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 症状日记
 */
@Data
@TableName("miaowang_pet_symptom_log")
@EqualsAndHashCode(callSuper = false)
public class SymptomLogDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 宠物ID
     */
    private Long pid;

    /**
     * 归属用户ID
     */
    private Long uid;

    /**
     * 用户名（冗余，便于排查）
     */
    private String username;

    /**
     * 知识库健康自查条目ID
     */
    private Long symptomId;

    /**
     * 症状名快照
     */
    private String symptomName;

    /**
     * 补充描述
     */
    private String description;

    /**
     * 严重程度：1=轻度 2=中度 3=重度
     */
    private Integer severity;

    /**
     * 症状出现时间
     */
    private Date occurredAt;

    /**
     * 症状照片 URL，逗号分隔
     */
    private String images;

    /**
     * 备注
     */
    private String remark;
}