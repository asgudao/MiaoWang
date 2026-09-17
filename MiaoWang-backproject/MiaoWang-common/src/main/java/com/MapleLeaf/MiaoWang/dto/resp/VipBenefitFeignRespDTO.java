package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 会员权益（Feign 跨服务调用的镜像对象）
 * 只镜像业务模块真正要用的字段，不追求与 VIP 模块的返回体一一对应
 */
@Data
public class VipBenefitFeignRespDTO {

    /**
     * 是否处于会员状态
     */
    private Boolean vip;

    /**
     * 每种物种（猫、狗各自）可养数量上限
     */
    private Integer petLimitPerType;

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