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
}