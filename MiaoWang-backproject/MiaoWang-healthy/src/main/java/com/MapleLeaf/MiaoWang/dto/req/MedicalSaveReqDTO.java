package com.MapleLeaf.MiaoWang.dto.req;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 就诊记录保存请求
 */
@Data
public class MedicalSaveReqDTO {

    /**
     * 就诊时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date visitAt;

    /**
     * 医院/诊所
     */
    private String hospital;

    /**
     * 接诊医生
     */
    private String doctor;

    /**
     * 诊断结果/病史
     */
    private String diagnosis;

    /**
     * 处置与治疗方案
     */
    private String treatment;

    /**
     * 费用（元）
     */
    private BigDecimal cost;

    /**
     * 化验单/影像附件 URL，逗号分隔
     */
    private String attachments;

    /**
     * 备注
     */
    private String remark;
}