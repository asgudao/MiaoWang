package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 知识碎片查询结果
 */
@Data
public class KnowledgeFragmentRespDTO {

    private Long id;

    /**
     * 所属分类编码
     */
    private String categoryCode;

    /**
     * 物种：1=猫 2=狗
     */
    private Integer species;

    /**
     * 品种ID，NULL=全品种通用
     */
    private Long breedId;

    /**
     * 条目名
     */
    private String title;

    /**
     * 主内容
     */
    private String content;

    /**
     * 备注/补充说明（如护理频率）
     */
    private String remark;

    /**
     * 适用阶段
     */
    private String stage;

    /**
     * 内容类型：care_schedule/guide/warning/medical
     */
    private String contentType;

    /**
     * 标签，逗号分隔
     */
    private String tags;

    /**
     * 来源：EXCEL/LLM/MANUAL
     */
    private String source;
}