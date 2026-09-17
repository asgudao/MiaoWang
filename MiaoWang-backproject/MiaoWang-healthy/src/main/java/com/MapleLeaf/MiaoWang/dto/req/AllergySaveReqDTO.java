package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 过敏史保存请求
 */
@Data
public class AllergySaveReqDTO {

    /**
     * 过敏原
     */
    private String allergen;

    /**
     * 严重程度：1=轻度 2=中度 3=重度
     */
    private Integer severity;

    /**
     * 过敏反应表现
     */
    private String reaction;

    /**
     * 应对方式/备注
     */
    private String note;
}