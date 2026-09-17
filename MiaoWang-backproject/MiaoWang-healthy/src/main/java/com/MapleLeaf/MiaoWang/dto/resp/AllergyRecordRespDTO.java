package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 过敏史
 */
@Data
public class AllergyRecordRespDTO {

    private Long id;

    private Long pid;

    private String allergen;

    /**
     * 1=轻度 2=中度 3=重度
     */
    private Integer severity;

    private String severityDesc;

    private String reaction;

    private String note;
}