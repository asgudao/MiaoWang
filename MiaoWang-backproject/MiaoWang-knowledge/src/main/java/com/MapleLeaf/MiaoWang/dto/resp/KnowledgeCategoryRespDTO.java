package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 知识分类查询结果
 */
@Data
public class KnowledgeCategoryRespDTO {

    private Long id;

    private String code;

    private String name;

    /**
     * 适用物种：0=通用 1=猫 2=狗
     */
    private Integer species;

    private Integer sort;
}