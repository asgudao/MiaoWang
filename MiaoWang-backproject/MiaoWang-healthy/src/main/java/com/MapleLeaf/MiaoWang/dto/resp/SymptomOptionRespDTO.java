package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 症状选项（来自知识库的「健康自查」条目）
 *
 * <p>症状不让用户自由填，而是从知识库里点选：只有结构化数据才能做统计、
 * 才能关联到对应的处置建议，自由文本写完就废了。
 */
@Data
public class SymptomOptionRespDTO {

    /**
     * 知识库条目ID（knowledge_fragment.id），写入症状日记时存这个
     */
    private Long symptomId;

    /**
     * 症状名（知识库条目名）
     */
    private String name;

    /**
     * 说明/自查要点
     */
    private String description;

    /**
     * 补充说明（如护理频率）
     */
    private String remark;

    /**
     * 物种：1=猫 2=狗
     */
    private Integer species;
}