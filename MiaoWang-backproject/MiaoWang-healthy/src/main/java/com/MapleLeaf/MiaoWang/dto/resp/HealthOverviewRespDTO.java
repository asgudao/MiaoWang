package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 健康档案概览：进入某只宠物健康页时的首屏数据
 *
 * <p>一次请求把「下次该打什么疫苗、最近体重变化、有哪些过敏、正在用什么药、
 * 各类记录记了多少条、额度是否用满」全部返回，避免首屏发六七个请求。
 */
@Data
public class HealthOverviewRespDTO {

    private Long pid;

    /**
     * 宠物名（来自 pet 服务）
     */
    private String petName;

    /**
     * 物种（来自 pet 服务）
     */
    private String species;

    /**
     * 档案总条数
     */
    private Integer totalRecords;

    /**
     * 各类记录条数，key 为记录类型（preventive/medical/...）
     */
    private Map<String, Long> recordCounts;

    /**
     * 当前档位每类记录上限，-1=不限
     */
    private Integer recordLimit;

    /**
     * 已经记满的类别（前端据此弹开通引导，而不是等用户提交时才报错）
     */
    private List<String> limitReachedTypes;

    /**
     * 最近一条需要关注的疫苗/驱虫（按下次到期时间升序取第一条）
     */
    private PreventiveRecordRespDTO nextPreventive;

    /**
     * 最近一次体重
     */
    private WeightRecordRespDTO latestWeight;

    /**
     * 相对 30 天前的变化（kg，正数=变重）
     */
    private BigDecimal weightChangeKg;

    /**
     * 过敏史
     */
    private List<AllergyRecordRespDTO> allergies;

    /**
     * 用药中的记录
     */
    private List<MedicationRecordRespDTO> activeMedications;

    /**
     * 当前档位能否导出报告
     */
    private Boolean exportAllowed;

    /**
     * 当前档位能否用 AI 解读症状
     */
    private Boolean aiAnalysisAllowed;
}