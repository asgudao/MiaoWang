package com.MapleLeaf.MiaoWang.dto.req;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 症状日记保存请求
 */
@Data
public class SymptomSaveReqDTO {

    /**
     * 知识库健康自查条目ID（用户从症状选项里选的）
     */
    private Long symptomId;

    /**
     * 症状名（后端会以知识库条目名称为准覆盖，防止前端乱传）
     */
    private String symptomName;

    /**
     * 补充描述
     */
    private String description;

    /**
     * 严重程度：1=轻度 2=中度 3=重度
     */
    private Integer severity;

    /**
     * 症状出现时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date occurredAt;

    /**
     * 症状照片 URL，逗号分隔
     */
    private String images;

    /**
     * 备注
     */
    private String remark;
}