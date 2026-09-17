package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 用药记录
 */
@Data
public class MedicationRecordRespDTO {

    private Long id;

    private Long pid;

    /**
     * 关联就诊记录ID
     */
    private Long medicalId;

    private String medicineName;

    private String dosage;

    private String frequency;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endAt;

    /**
     * 0=用药中 1=已结束 2=已停用
     */
    private Integer status;

    /**
     * 状态中文名
     */
    private String statusDesc;

    private String remark;
}