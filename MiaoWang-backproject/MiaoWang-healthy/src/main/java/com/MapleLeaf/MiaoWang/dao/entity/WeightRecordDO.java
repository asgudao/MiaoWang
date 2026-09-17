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
 * 体重记录
 */
@Data
@TableName("miaowang_pet_weight_record")
@EqualsAndHashCode(callSuper = false)
public class WeightRecordDO extends BaseDO {

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
     * 体重（kg）
     */
    private BigDecimal weightKg;

    /**
     * 测量时间
     */
    private Date measuredAt;

    /**
     * 备注
     */
    private String note;
}