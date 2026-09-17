package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 当前用户会员权益总览
 * 未开会员时返回免费档权益（vip=false），前端不用区分两套接口
 */
@Data
public class UserVipBenefitRespDTO {

    /**
     * 是否处于会员状态
     */
    private Boolean vip;

    /**
     * 生效中的档位名称列表
     */
    private List<String> planNames;

    /**
     * 最晚的到期时间（无会员为 null）
     */
    private Date expireTime;

    /**
     * 每种物种（猫、狗各自）可养数量上限；健康档案可建档的宠物数同此值
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
     * RAG 额度周期描述（每日/每月），前端直接展示，不用自己翻译
     */
    private String ragPeriodDesc;

    /**
     * 本周期内 RAG 已用次数，-1=不限（不计数）
     */
    private Integer ragUsed;

    /**
     * 本周期内 RAG 剩余次数，-1=不限
     */
    private Integer ragRemaining;

    /**
     * 聊天轮次上限，-1=不限
     */
    private Integer chatLimit;

    /**
     * 聊天额度周期：DAY=每日 MONTH=每月
     */
    private String chatPeriod;

    /**
     * 聊天额度周期描述（每日/每月）
     */
    private String chatPeriodDesc;

    /**
     * 本周期内聊天已用轮次，-1=不限（不计数）
     */
    private Integer chatUsed;

    /**
     * 本周期内聊天剩余轮次，-1=不限
     */
    private Integer chatRemaining;

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
}