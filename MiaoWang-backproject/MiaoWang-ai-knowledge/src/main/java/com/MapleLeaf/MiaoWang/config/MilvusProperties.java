package com.MapleLeaf.MiaoWang.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 向量数据库配置
 */
@Data
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    /**
     * Milvus gRPC 地址
     */
    private String uri = "http://192.168.137.128:19530";

    /**
     * 知识碎片向量集合名
     */
    private String collectionName = "miao_wang_knowledge_fragment";

    /**
     * 向量维度（与 embedding 模型维度一致，默认 1024）
     */
    private Integer dimension = 1024;

    /**
     * 单次向量检索取回条数
     */
    private Integer topK = 10;
}