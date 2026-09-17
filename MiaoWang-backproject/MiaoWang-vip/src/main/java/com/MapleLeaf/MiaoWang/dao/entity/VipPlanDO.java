package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 会员档位配置
 * 所有权益数值都在这里，代码里不写死（改权益 = 改数据）
 */
@Data
@TableName("miaowang_vip_plan")
@EqualsAndHashCode(callSuper = false)
public class VipPlanDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 档位编码：FREE/PET_49/AI_149/AI_249/AI_349/ALL_400
     */
    private String planCode;

    /**
     * 档位名称
     */
    private String planName;

    /**
     * 月费（元）
     */
    private BigDecimal price;

    /**
     * 单次开通/续费的有效天数
     */
    private Integer durationDays;

    /**
     * 每种物种（猫、狗各自）可养数量上限（健康档案可建档的宠物数同此值）
     */
    private Integer petLimitPerType;

    /**
     * RAG 次数上限，-1=不限次数
     */
    private Integer ragLimit;

    /**
     * RAG 额度周期：DAY=每日 MONTH=每月
     */
    private String ragPeriod;

    /**
     * 宠物聊天轮次上限，-1=不限次数
     */
    private Integer chatLimit;

    /**
     * 聊天额度周期：DAY=每日 MONTH=每月
     */
    private String chatPeriod;

    /**
     * 每只宠物每类健康记录条数上限，-1=不限
     */
    private Integer healthRecordLimit;

    /**
     * 是否可导出健康报告：0=否 1=是
     */
    private Integer healthExport;

    /**
     * 是否可用 AI 解读症状：0=否 1=是
     */
    private Integer healthAiAnalysis;

    /**
     * 提醒条数上限，-1=不限
     */
    private Integer reminderLimit;

    /**
     * 是否可购买：0=否 1=是
     */
    private Integer purchasable;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}