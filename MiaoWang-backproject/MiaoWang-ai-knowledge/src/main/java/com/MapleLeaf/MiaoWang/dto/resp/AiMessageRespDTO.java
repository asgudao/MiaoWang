package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 一条消息（回放历史会话用）
 */
@Data
public class AiMessageRespDTO {

    private Long id;

    /**
     * 角色：1=用户提问 2=AI回答
     */
    private Integer role;

    private String content;

    /**
     * AI 行：是否命中知识库
     */
    private Boolean hasKnowledge;

    /**
     * AI 行：当时引用的知识碎片（已把库里的 JSON 解析成对象返回，前端直接用）
     */
    private List<RagSourceRespDTO> sources;

    /**
     * 反馈：null=未评价 1=👍 2=👎
     */
    private Integer feedback;

    private Date createTime;
}