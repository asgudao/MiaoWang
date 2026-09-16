package com.MapleLeaf.MiaoWang.dto.resp;

import com.MapleLeaf.MiaoWang.dto.resp.RagSourceRespDTO;
import lombok.Data;

import java.util.List;

/**
 * RAG 问答响应
 */
@Data
public class RagAskRespDTO {

    /**
     * 大模型生成的回答
     */
    private String answer;

    /**
     * 是否命中知识库（false 表示没有检索到相关内容）
     */
    private Boolean hasKnowledge;

    /**
     * 回答依据的知识碎片来源（引用溯源）
     */
    private List<RagSourceRespDTO> sources;
}