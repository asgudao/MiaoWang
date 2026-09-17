package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * AI 症状解读结果
 * 只做"可能原因 / 需要观察什么 / 何时必须就医"的辅助参考，不给用药剂量
 */
@Data
public class HealthAiAnalyzeRespDTO {

    /**
     * 宠物ID
     */
    private Long pid;

    /**
     * 宠物名
     */
    private String petName;

    /**
     * 参与本次解读的最近症状条数（前端可以标注"基于最近 N 条症状"）
     */
    private Integer symptomCount;

    /**
     * AI 解读正文
     */
    private String analysis;

    /**
     * 是否命中知识库：false 说明知识库还没覆盖这类症状，解读只靠模型自身知识，可信度要打折
     */
    private Boolean hasKnowledge;

    /**
     * 解读依据的知识库条目（前端应展示出来，避免"AI 说什么就是什么"）
     */
    private List<AiRagSourceRespDTO> sources;

    /**
     * 免责声明（前端必须展示）
     */
    private String disclaimer;

    /**
     * 生成时间
     */
    private Date generatedAt;
}