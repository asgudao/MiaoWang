package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 健康时间线上的一条
 *
 * <p>把六类记录拍平成同一种形状，前端一个列表组件就能渲染全部档案，
 * 不用为每种记录写一套样式。
 */
@Data
public class HealthTimelineItemRespDTO {

    /**
     * 记录类型：preventive/medical/medication/allergy/symptom/weight
     */
    private String recordType;

    /**
     * 记录类型中文名
     */
    private String recordTypeDesc;

    /**
     * 对应记录的ID（点进去看详情用）
     */
    private Long recordId;

    /**
     * 标题，如：猫三联 / 呕吐 / 体重 4.2kg
     */
    private String title;

    /**
     * 摘要，如诊断结果、症状描述
     */
    private String summary;

    /**
     * 发生时间（时间线排序依据）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date happenedAt;

    /**
     * 严重程度（仅过敏史/症状日记有值）
     */
    private Integer severity;
}