package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 额度扣减/查询结果
 * 不抛异常的返回形式：调用方拿 allowed 判断即可，避免把「次数用完」当成系统错误
 */
@Data
public class VipQuotaRespDTO {

    /**
     * 业务类型：rag / chat
     */
    private String biz;

    /**
     * 业务描述
     */
    private String bizDesc;

    /**
     * 额度周期：DAY=每日 MONTH=每月
     */
    private String period;

    /**
     * 额度周期描述（每日/每月）
     */
    private String periodDesc;

    /**
     * 次数上限，-1=不限
     */
    private Integer limit;

    /**
     * 本周期内已用次数，-1=不限（不计数）
     */
    private Integer used;

    /**
     * 本周期内剩余次数，-1=不限
     */
    private Integer remaining;

    /**
     * 本次是否允许使用
     */
    private Boolean allowed;

    /**
     * 提示信息
     */
    private String message;
}