package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 社区版块返回
 */
@Data
public class SectionRespDTO {

    private Long sectionId;

    /**
     * 版块名称，如：宠物美照
     */
    private String name;

    /**
     * 版块编码：photo/knowledge/baby/breed/symptom/activity
     */
    private String code;

    /**
     * 版块简介
     */
    private String description;

    /**
     * 版块图标 URL
     */
    private String iconUrl;

    /**
     * 排序值
     */
    private Integer sort;
}