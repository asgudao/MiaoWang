package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 会员额度（Feign 跨服务调用的镜像对象）
 * 只镜像本模块要用的字段：allowed 决定放不放行，limit=-1 表示不限次数（不计数）
 */
@Data
public class VipQuotaFeignRespDTO {

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
     * 本周期内已用次数，-1=不限
     */
    private Integer used;

    /**
     * 本周期内剩余次数，-1=不限
     */
    private Integer remaining;

    /**
     * 是否允许本次使用
     */
    private Boolean allowed;

    /**
     * 提示信息（次数用完时可直接透给前端）
     */
    private String message;
}