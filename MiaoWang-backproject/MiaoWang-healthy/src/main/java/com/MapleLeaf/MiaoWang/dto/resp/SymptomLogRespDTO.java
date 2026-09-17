package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 症状日记
 */
@Data
public class SymptomLogRespDTO {

    private Long id;

    private Long pid;

    /**
     * 知识库健康自查条目ID
     */
    private Long symptomId;

    /**
     * 症状名快照
     */
    private String symptomName;

    private String description;

    /**
     * 1=轻度 2=中度 3=重度
     */
    private Integer severity;

    private String severityDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date occurredAt;

    /**
     * 症状照片列表
     */
    private List<String> images;

    private String remark;
}