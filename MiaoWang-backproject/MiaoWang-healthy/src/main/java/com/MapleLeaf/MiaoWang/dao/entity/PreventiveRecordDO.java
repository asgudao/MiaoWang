package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 疫苗 / 驱虫记录（合并表）
 */
@Data
@TableName("miaowang_pet_preventive_record")
@EqualsAndHashCode(callSuper = false)
public class PreventiveRecordDO extends BaseDO {

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
     * 类型：VACCINE=疫苗 DEWORM_IN=体内驱虫 DEWORM_OUT=体外驱虫
     */
    private String type;

    /**
     * 项目名，如：猫三联、狂犬疫苗、大宠爱
     */
    private String itemName;

    /**
     * 接种/驱虫时间
     */
    private Date happenedAt;

    /**
     * 下次到期时间
     */
    private Date nextDueAt;

    /**
     * 医院/机构
     */
    private String hospital;

    /**
     * 疫苗批号
     */
    private String batchNo;

    /**
     * 备注
     */
    private String remark;
}