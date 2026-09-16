package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Builder;
import lombok.Data;

/**
 * RAG 回答的来源引用（对应知识碎片）
 */
@Data
@Builder
public class RagSourceRespDTO {

    /**
     * 知识碎片 id
     */
    private Long id;

    /**
     * 条目名
     */
    private String title;

    /**
     * 物种：1=猫 2=狗
     */
    private Integer species;

    /**
     * 分类编码：diet/behavior/care/health
     */
    private String categoryCode;
}