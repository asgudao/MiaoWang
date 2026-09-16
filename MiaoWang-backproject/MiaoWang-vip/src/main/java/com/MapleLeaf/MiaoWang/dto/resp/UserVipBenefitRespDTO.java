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
     * 每种物种（猫、狗各自）可养数量上限
     */
    private Integer petLimitPerType;

    /**
     * RAG 每日次数上限，-1=不限
     */
    private Integer ragDailyLimit;

    /**
     * 宠物聊天每日轮次上限，-1=不限
     */
    private Integer chatDailyLimit;

    /**
     * RAG 今日已用次数，-1=不限（不计数）
     */
    private Integer ragUsedToday;

    /**
     * RAG 今日剩余次数，-1=不限
     */
    private Integer ragRemainingToday;

    /**
     * 聊天今日已用轮次，-1=不限（不计数）
     */
    private Integer chatUsedToday;

    /**
     * 聊天今日剩余轮次，-1=不限
     */
    private Integer chatRemainingToday;
}