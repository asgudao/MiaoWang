package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.resp.HealthExportRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthOverviewRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthTimelineItemRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.SymptomOptionRespDTO;

import java.util.List;

/**
 * 健康档案聚合服务：概览、时间线、症状选项、导出
 */
public interface HealthOverviewService {

    /**
     * 档案概览（首屏一次拿全）
     */
    HealthOverviewRespDTO overview(Long pid);

    /**
     * 健康时间线：把六类记录拍平成一条按时间倒序的列表
     *
     * @param types 记录类型筛选，逗号分隔，如 preventive,symptom；不传返回全部
     * @param limit 返回条数上限，默认 50，最大 200
     */
    List<HealthTimelineItemRespDTO> timeline(Long pid, String types, Integer limit);

    /**
     * 症状选项（来自知识库「健康自查」条目）
     *
     * @param species 物种：1=猫 2=狗
     */
    List<SymptomOptionRespDTO> symptomOptions(Integer species);

    /**
     * 导出完整健康档案（结构化 JSON，需要档位支持导出）
     */
    HealthExportRespDTO export(Long pid);
}