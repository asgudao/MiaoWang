package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

import java.util.List;

/**
 * AI 解读服务返回（Feign 跨服务调用的镜像对象）
 */
@Data
public class AiRagAskRespDTO {

    /**
     * 大模型生成的回答
     */
    private String answer;

    /**
     * 是否命中知识库（false 表示没有检索到相关内容，属于"知识库还没覆盖"）
     */
    private Boolean hasKnowledge;

    /**
     * 回答依据的知识碎片来源
     */
    private List<AiRagSourceRespDTO> sources;
}