package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.MedicalSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.MedicationSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PreventiveSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicalRecordRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.MedicationRecordRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PreventiveRecordRespDTO;
import com.MapleLeaf.MiaoWang.service.MedicalRecordService;
import com.MapleLeaf.MiaoWang.service.MedicationRecordService;
import com.MapleLeaf.MiaoWang.service.PreventiveRecordService;
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
 * 健康档案：疫苗驱虫 / 就诊 / 用药
 * <p>
 * 路径约定：新增与列表带 pid（宠物维度），修改与删除带记录 id（记录维度）
 * 宠物归属、记录归属与额度校验都在 service 层，控制器只做参数搬运
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/mapleleaf/miaowang/v1/health")
public class HealthRecordController {

    private final PreventiveRecordService preventiveRecordService;

    private final MedicalRecordService medicalRecordService;

    private final MedicationRecordService medicationRecordService;

    /**
     * 新增疫苗/驱虫记录
     */
    @PostMapping("/{pid}/preventive")
    public Result<Long> createPreventive(@PathVariable("pid") Long pid,
                                        @RequestBody PreventiveSaveReqDTO requestParam) {
        return Results.success(preventiveRecordService.create(pid, requestParam));
    }

    /**
     * 疫苗/驱虫记录列表
     *
     * @param type 可选：VACCINE / DEWORM_IN / DEWORM_OUT
     */
    @GetMapping("/{pid}/preventive")
    public Result<List<PreventiveRecordRespDTO>> listPreventive(@PathVariable("pid") Long pid,
                                                                @RequestParam(value = "type", required = false) String type) {
        return Results.success(preventiveRecordService.list(pid, type));
    }

    /**
     * 修改疫苗/驱虫记录
     */
    @PutMapping("/preventive/{id}")
    public Result<Void> updatePreventive(@PathVariable("id") Long id,
                                        @RequestBody PreventiveSaveReqDTO requestParam) {
        preventiveRecordService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除疫苗/驱虫记录
     */
    @DeleteMapping("/preventive/{id}")
    public Result<Void> deletePreventive(@PathVariable("id") Long id) {
        preventiveRecordService.delete(id);
        return Results.success();
    }

    /**
     * 新增就诊记录
     */
    @PostMapping("/{pid}/medical")
    public Result<Long> createMedical(@PathVariable("pid") Long pid,
                                     @RequestBody MedicalSaveReqDTO requestParam) {
        return Results.success(medicalRecordService.create(pid, requestParam));
    }

    /**
     * 就诊记录列表（同时就是病史列表）
     */
    @GetMapping("/{pid}/medical")
    public Result<List<MedicalRecordRespDTO>> listMedical(@PathVariable("pid") Long pid) {
        return Results.success(medicalRecordService.list(pid));
    }

    /**
     * 修改就诊记录
     */
    @PutMapping("/medical/{id}")
    public Result<Void> updateMedical(@PathVariable("id") Long id,
                                     @RequestBody MedicalSaveReqDTO requestParam) {
        medicalRecordService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除就诊记录
     */
    @DeleteMapping("/medical/{id}")
    public Result<Void> deleteMedical(@PathVariable("id") Long id) {
        medicalRecordService.delete(id);
        return Results.success();
    }

    /**
     * 新增用药记录
     */
    @PostMapping("/{pid}/medication")
    public Result<Long> createMedication(@PathVariable("pid") Long pid,
                                        @RequestBody MedicationSaveReqDTO requestParam) {
        return Results.success(medicationRecordService.create(pid, requestParam));
    }

    /**
     * 用药记录列表
     *
     * @param status 可选：0=用药中 1=已结束 2=已停用
     */
    @GetMapping("/{pid}/medication")
    public Result<List<MedicationRecordRespDTO>> listMedication(@PathVariable("pid") Long pid,
                                                               @RequestParam(value = "status", required = false) Integer status) {
        return Results.success(medicationRecordService.list(pid, status));
    }

    /**
     * 修改用药记录（结束用药也走这里：status=1 并补上 endAt）
     */
    @PutMapping("/medication/{id}")
    public Result<Void> updateMedication(@PathVariable("id") Long id,
                                        @RequestBody MedicationSaveReqDTO requestParam) {
        medicationRecordService.update(id, requestParam);
        return Results.success();
    }

    /**
     * 删除用药记录
     */
    @DeleteMapping("/medication/{id}")
    public Result<Void> deleteMedication(@PathVariable("id") Long id) {
        medicationRecordService.delete(id);
        return Results.success();
    }
}