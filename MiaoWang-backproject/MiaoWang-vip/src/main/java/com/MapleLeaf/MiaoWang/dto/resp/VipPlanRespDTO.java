package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员档位（前端会员页展示用）
 */
@Data
public class VipPlanRespDTO {

    /**
     * 档位编码
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
     * 有效天数
     */
    private Integer durationDays;

    /**
     * 每种物种（猫、狗各自）可养数量上限
     */
    private Integer petLimitPerType;

    /**
     * RAG 次数上限，-1=不限
     */
    private Integer ragLimit;

    /**
     * RAG 额度周期：DAY=每日 MONTH=每月
     */
    private String ragPeriod;

    /**
     * 聊天轮次上限，-1=不限
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
     * 排序值
     */
    private Integer sort;
}