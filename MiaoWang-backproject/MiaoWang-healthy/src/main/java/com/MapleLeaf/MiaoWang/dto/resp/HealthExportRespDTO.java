package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 健康档案导出包（结构化 JSON）
 *
 * <p>先出结构化数据，导出 PDF/图片由前端或后续的模板渲染来做：
 * 后端只负责「把用户的数据完整、干净地交给它」，不掺排版逻辑。
 * 导出属于付费权益，调用前会校验档位。
 */
@Data
public class HealthExportRespDTO {

    private Long pid;

    private String petName;

    private String species;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date exportedAt;

    /**
     * 当前档位每类记录上限
     */
    private Integer recordLimit;

    /**
     * 各类记录条数
     */
    private Map<String, Long> recordCounts;

    private List<PreventiveRecordRespDTO> preventives;

    private List<MedicalRecordRespDTO> medicals;

    private List<MedicationRecordRespDTO> medications;

    private List<AllergyRecordRespDTO> allergies;

    private List<SymptomLogRespDTO> symptoms;

    private List<WeightRecordRespDTO> weights;
}