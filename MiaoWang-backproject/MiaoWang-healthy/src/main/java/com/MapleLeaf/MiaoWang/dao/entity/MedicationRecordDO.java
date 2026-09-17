package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 用药记录
 */
@Data
@TableName("miaowang_pet_medication_record")
@EqualsAndHashCode(callSuper = false)
public class MedicationRecordDO extends BaseDO {

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
     * 关联就诊记录ID（可空：日常用药没有就诊单）
     */
    private Long medicalId;

    /**
     * 药品名
     */
    private String medicineName;

    /**
     * 单次用量，如：半片、5ml
     */
    private String dosage;

    /**
     * 频次，如：每日2次
     */
    private String frequency;

    /**
     * 开始用药时间
     */
    private Date startAt;

    /**
     * 结束时间（空=仍在用药）
     */
    private Date endAt;

    /**
     * 状态：0=用药中 1=已结束 2=已停用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}