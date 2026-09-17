package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.AllergySaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.SymptomSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.WeightSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AllergyRecordRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.SymptomLogRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.WeightCurvePointRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.WeightRecordRespDTO;
import com.MapleLeaf.MiaoWang.service.AllergyRecordService;
import com.MapleLeaf.MiaoWang.service.SymptomLogService;
import com.MapleLeaf.MiaoWang.service.WeightRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 健康档案：过敏史 / 症状日记 / 体重
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mapleleaf/miaowang/v1/health")
public class HealthDailyRecordController {

    private final AllergyRecordService allergyRecordService;

    private final SymptomLogService symptomLogService;

    private final WeightRecordService weightRecordService;

    /**
     * 新增过敏记录
     */
    @PostMapping("/{pid}/allergy")
    public Result<Long> createAllergy(@PathVariable("pid") Long pid,
                                     @RequestBody AllergySaveReqDTO requestParam) {
        return Results.success(allergyRecordService.create(pid, requestParam));
    }

    /**
     * 过敏史列表（重度在前，方便一眼看到要注意的东西）
     */
    @GetMapping("/{pid}/allergy")
    public Result<List<AllergyRecordRespDTO>> listAllergy(@PathVariable("pid") Long pid) {
        return Results.success(allergyRecordService.list(pid));
    }

    /**
     * 修改过敏记录
     */
    @PutMapping("/allergy/{id}")
    public Result<Void> updateAllergy(@PathVariable("id") Long id,
                                     @RequestBody AllergySaveReqDTO requestParam) {
        allergyRecordService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除过敏记录
     */
    @DeleteMapping("/allergy/{id}")
    public Result<Void> deleteAllergy(@PathVariable("id") Long id) {
        allergyRecordService.delete(id);
        return Results.success();
    }

    /**
     * 新增症状日记（symptomId 来自 /health/symptom/options，症状名以后端回填为准）
     */
    @PostMapping("/{pid}/symptom")
    public Result<Long> createSymptom(@PathVariable("pid") Long pid,
                                     @RequestBody SymptomSaveReqDTO requestParam) {
        return Results.success(symptomLogService.create(pid, requestParam));
    }

    /**
     * 症状日记列表
     */
    @GetMapping("/{pid}/symptom")
    public Result<List<SymptomLogRespDTO>> listSymptom(@PathVariable("pid") Long pid) {
        return Results.success(symptomLogService.list(pid));
    }

    /**
     * 修改症状日记
     */
    @PutMapping("/symptom/{id}")
    public Result<Void> updateSymptom(@PathVariable("id") Long id,
                                     @RequestBody SymptomSaveReqDTO requestParam) {
        symptomLogService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除症状日记
     */
    @DeleteMapping("/symptom/{id}")
    public Result<Void> deleteSymptom(@PathVariable("id") Long id) {
        symptomLogService.delete(id);
        return Results.success();
    }

    /**
     * 新增体重记录
     */
    @PostMapping("/{pid}/weight")
    public Result<Long> createWeight(@PathVariable("pid") Long pid,
                                    @RequestBody WeightSaveReqDTO requestParam) {
        return Results.success(weightRecordService.create(pid, requestParam));
    }

    /**
     * 体重记录列表
     */
    @GetMapping("/{pid}/weight")
    public Result<List<WeightRecordRespDTO>> listWeight(@PathVariable("pid") Long pid) {
        return Results.success(weightRecordService.list(pid));
    }

    /**
     * 体重曲线（按天聚合，每天取最后一次测量）
     *
     * @param months 回溯月数，默认 6，最大 24
     */
    @GetMapping("/{pid}/weight/curve")
    public Result<List<WeightCurvePointRespDTO>> weightCurve(@PathVariable("pid") Long pid,
                                                             @RequestParam(value = "months", required = false) Integer months) {
        return Results.success(weightRecordService.curve(pid, months));
    }

    /**
     * 修改体重记录
     */
    @PutMapping("/weight/{id}")
    public Result<Void> updateWeight(@PathVariable("id") Long id,
                                    @RequestBody WeightSaveReqDTO requestParam) {
        weightRecordService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除体重记录
     */
    @DeleteMapping("/weight/{id}")
    public Result<Void> deleteWeight(@PathVariable("id") Long id) {
        weightRecordService.delete(id);
        return Results.success();
    }
}