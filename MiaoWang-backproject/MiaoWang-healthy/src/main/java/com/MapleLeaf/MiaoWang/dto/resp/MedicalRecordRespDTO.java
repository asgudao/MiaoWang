package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 就诊记录
 */
@Data
public class MedicalRecordRespDTO {

    private Long id;

    private Long pid;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date visitAt;

    private String hospital;

    private String doctor;

    private String diagnosis;

    private String treatment;

    private BigDecimal cost;

    /**
     * 附件列表：库里存的是逗号分隔字符串，出口统一拆成数组，省得每个前端各写一遍
     */
    private List<String> attachments;

    private String remark;
}