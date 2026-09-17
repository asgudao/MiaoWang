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

    /**
     * 向量检索的最低相似度阈值（COSINE，范围 -1~1，越大越相似）。
     * 低于该值的碎片会被丢弃，避免"知识库里明明没有相关内容，却硬塞几条无关碎片给模型"。
     * <p>
     * 这个值需要按你的 embedding 模型实测标定，方法：
     * 问一个知识库里【有】的问题 + 一个【没有】的问题，对比日志里 "向量检索：命中 N 条，分数 [...]"，
     * 把阈值卡在两者的分数之间即可。设为 0 或负数表示不启用过滤。
     * <p>
     * 可在 Nacos 的 {@code milvus.similarity-threshold} 覆盖，无需改代码重编译。
     */
    private Double similarityThreshold = 0.35;
}