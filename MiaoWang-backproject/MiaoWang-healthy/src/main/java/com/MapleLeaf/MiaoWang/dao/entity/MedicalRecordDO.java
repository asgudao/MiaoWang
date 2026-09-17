package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 就诊记录 / 病史
 */
@Data
@TableName("miaowang_pet_medical_record")
@EqualsAndHashCode(callSuper = false)
public class MedicalRecordDO extends BaseDO {

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
     * 就诊时间
     */
    private Date visitAt;

    /**
     * 医院/诊所
     */
    private String hospital;

    /**
     * 接诊医生
     */
    private String doctor;

    /**
     * 诊断结果/病史
     */
    private String diagnosis;

    /**
     * 处置与治疗方案
     */
    private String treatment;

    /**
     * 费用（元）
     */
    private BigDecimal cost;

    /**
     * 化验单/影像附件 URL，逗号分隔
     */
    private String attachments;

    /**
     * 备注
     */
    private String remark;
}