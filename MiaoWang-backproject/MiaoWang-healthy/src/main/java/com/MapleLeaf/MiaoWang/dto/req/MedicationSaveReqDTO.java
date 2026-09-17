package com.MapleLeaf.MiaoWang.dto.req;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 用药记录保存请求
 */
@Data
public class MedicationSaveReqDTO {

    /**
     * 关联就诊记录ID（可空）
     */
    private Long medicalId;

    /**
     * 药品名
     */
    private String medicineName;

    /**
     * 单次用量，如：半片、5ml
     */
    private String dosage;

    /**
     * 频次，如：每日2次
     */
    private String frequency;

    /**
     * 开始用药时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startAt;

    /**
     * 结束时间（空=仍在用药）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endAt;

    /**
     * 状态：0=用药中 1=已结束 2=已停用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}