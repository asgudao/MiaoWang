package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.HealthExportRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthOverviewRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.HealthTimelineItemRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.SymptomOptionRespDTO;
import com.MapleLeaf.MiaoWang.service.HealthOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 健康档案：概览 / 时间线 / 症状选项 / 导出
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mapleleaf/miaowang/v1/health")
public class HealthOverviewController {

    private final HealthOverviewService healthOverviewService;

    /**
     * 概览：首屏一次拿全（各类记录条数、下次疫苗到期、体重变化、过敏、在用药物、权益）
     */
    @GetMapping("/{pid}/overview")
    public Result<HealthOverviewRespDTO> overview(@PathVariable("pid") Long pid) {
        return Results.success(healthOverviewService.overview(pid));
    }

    /**
     * 时间线：六类记录按时间倒序合并
     *
     * @param types 可选，逗号分隔，如 preventive,symptom,weight
     * @param limit 可选，默认 50，最大 200
     */
    @GetMapping("/{pid}/timeline")
    public Result<List<HealthTimelineItemRespDTO>> timeline(@PathVariable("pid") Long pid,
                                                            @RequestParam(value = "types", required = false) String types,
                                                            @RequestParam(value = "limit", required = false) Integer limit) {
        return Results.success(healthOverviewService.timeline(pid, types, limit));
    }

    /**
     * 症状选项（知识库「健康自查」条目，让用户点选而不是自由填写）
     *
     * @param species 1=猫 2=狗，不传返回全部
     */
    @GetMapping("/symptom/options")
    public Result<List<SymptomOptionRespDTO>> symptomOptions(@RequestParam(value = "species", required = false) Integer species) {
        return Results.success(healthOverviewService.symptomOptions(species));
    }

    /**
     * 导出完整健康档案（结构化 JSON，需要档位支持导出）
     */
    @GetMapping("/{pid}/export")
    public Result<HealthExportRespDTO> export(@PathVariable("pid") Long pid) {
        return Results.success(healthOverviewService.export(pid));
    }
}