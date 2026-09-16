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
     * RAG 每日次数，-1=不限
     */
    private Integer ragDailyLimit;

    /**
     * 宠物聊天每日轮次，-1=不限
     */
    private Integer chatDailyLimit;

    /**
     * 排序值
     */
    private Integer sort;
}