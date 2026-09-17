package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * AI 解读请求（本模块调用 ai-knowledge 的 {@code /ai/rag/internal/analyze} 用）
 *
 * <p>刻意在本模块自定义一份镜像，而不是依赖 ai-knowledge 的 DTO：
 * 服务之间只约定 JSON 结构，不依赖对方代码。
 */
@Data
public class AiRagAskReqDTO {

    /**
     * 提问内容（由健康模块根据症状记录拼好）
     */
    private String question;

    /**
     * 物种过滤：1=猫 2=狗，不传则不做物种过滤
     */
    private Integer species;

    /**
     * 发起解读的用户ID：仅用于对端记调用日志（成本归因），不是鉴权依据
     */
    private Long uid;

    public static AiRagAskReqDTO of(String question, Integer species, Long uid) {
        AiRagAskReqDTO requestParam = new AiRagAskReqDTO();
        requestParam.setQuestion(question);
        requestParam.setSpecies(species);
        requestParam.setUid(uid);
        return requestParam;
    }
}