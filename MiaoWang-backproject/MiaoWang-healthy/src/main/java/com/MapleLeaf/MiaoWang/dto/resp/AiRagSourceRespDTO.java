package com.MapleLeaf.MiaoWang.dto.resp;


import lombok.Data;

/**
 * 知识库来源引用（Feign 跨服务调用的镜像对象）
 */
@Data
public class AiRagSourceRespDTO {

    /**
     * 知识碎片 id
     */
    private Long id;

    /**
     * 条目名
     */
    private String title;

    /**
     * 分类编码：diet/behavior/care/health
     */
    private String categoryCode;
}