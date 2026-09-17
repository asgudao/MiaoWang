package com.MapleLeaf.MiaoWang.support;


import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 权益解析结果（模块内部使用，不对外暴露）
 * 把「用户当前持有哪些档位」合并成一份最终权益
 */
@Data
public class VipBenefit {

    /**
     * 是否处于会员状态
     */
    private Boolean vip;

    /**
     * 生效中的档位名称列表
     */
    private List<String> planNames;

    /**
     * 最晚的到期时间
     */
    private Date expireTime;

    /**
     * 每种物种（猫、狗各自）可养数量上限（健康档案可建档的宠物数同此值）
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
     * 宠物聊天轮次上限，-1=不限
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
}