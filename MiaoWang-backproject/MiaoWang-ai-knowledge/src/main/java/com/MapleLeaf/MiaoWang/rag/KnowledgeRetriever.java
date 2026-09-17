package com.MapleLeaf.MiaoWang.rag;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.config.MilvusProperties;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;
import com.MapleLeaf.MiaoWang.openfegin.knowledge.KnowledgeServiceFeign;
import com.MapleLeaf.MiaoWang.rag.milvus.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识检索器：向量（Milvus）+ 关键词（knowledge 模块 MySQL LIKE）双路召回，RRF 融合。
 * <p>
 * 这是混合检索的核心：
 * <pre>
 *   问题 → ① embedding → Milvus 向量 topK（语义相似）
 *        → ② 提取关键词 → Feign 调 knowledge 服务 LIKE 检索（字面精确）
 *        → RRF(1/(60+rank)) 按名次融合 → 取 topN 作为 LLM 上下文
 * </pre>
 */
@Slf4j
@Component
public class KnowledgeRetriever {

    private static final int RRF_K = 60;
    private static final int KEYWORD_TOP_K = 20;

    private final MilvusVectorStore milvusVectorStore;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeServiceFeign knowledgeServiceFeign;
    private final MilvusProperties milvusProperties;

    public KnowledgeRetriever(MilvusVectorStore milvusVectorStore,
                              EmbeddingModel embeddingModel,
                              KnowledgeServiceFeign knowledgeServiceFeign,
                              MilvusProperties milvusProperties) {
        this.milvusVectorStore = milvusVectorStore;
        this.embeddingModel = embeddingModel;
        this.knowledgeServiceFeign = knowledgeServiceFeign;
        this.milvusProperties = milvusProperties;
    }

    /**
     * 检索结果：知识碎片 + RRF 融合分
     */
    public record RetrievedFragment(Long id, String title, String content, Integer species,
                                    String categoryCode, double rrfScore) {
    }

    /**
     * 混合检索：返回按 RRF 融合分排序的 topN 碎片
     */
    public List<RetrievedFragment> retrieve(String question, Integer species) {
        Integer effectiveSpecies = species != null ? species : detectSpecies(question);
        // ① 向量路
        List<MilvusVectorStore.Hit> vectorHits = new ArrayList<>();
        try {
            float[] queryVector = embeddingModel.embed(question);
            List<MilvusVectorStore.Hit> rawHits =
                    milvusVectorStore.search(queryVector, milvusProperties.getTopK(), effectiveSpecies);
            // 关键一步：Milvus 的 topK 检索永远会返回最像的 K 条（哪怕全都不相关），
            // 必须按相似度过滤，否则无关碎片会污染上下文、让 hasKnowledge 失真
            vectorHits = filterBySimilarity(rawHits);
        } catch (Exception e) {
            log.warn("向量检索失败，仅用关键词路: {}", e.getMessage());
        }
        // ② 关键词路
        List<MilvusVectorStore.Hit> keywordHits = keywordSearch(question, effectiveSpecies);

        // ③ RRF 融合（只按名次，不看分数量纲）
        Map<Long, Double> scoreMap = new LinkedHashMap<>();
        Map<Long, MilvusVectorStore.Hit> hitMap = new LinkedHashMap<>();
        addRanked(vectorHits, scoreMap, hitMap);
        addRanked(keywordHits, scoreMap, hitMap);

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(Comparator.reverseOrder()))
                .map(e -> {
                    MilvusVectorStore.Hit h = hitMap.get(e.getKey());
                    return new RetrievedFragment(h.id(), h.title(), h.content(), h.species(),
                            h.categoryCode(), e.getValue());
                })
                .limit(5)
                .toList();
    }

    /**
     * 关键词召回路：提取术语 → Feign 逐个词查 knowledge 服务 → 合并去重
     */
    private List<MilvusVectorStore.Hit> keywordSearch(String question, Integer species) {
        List<MilvusVectorStore.Hit> hits = new ArrayList<>();
        List<String> terms = KeywordTermExtractor.extract(question, 4);
        for (String term : terms) {
            try {
                Result<List<KnowledgeFragmentRespDTO>> result =
                        knowledgeServiceFeign.searchFragments(term, species, null);
                if (result != null && result.getData() != null) {
                    for (KnowledgeFragmentRespDTO dto : result.getData()) {
                        hits.add(new MilvusVectorStore.Hit(dto.getId(), 1.0f, dto.getTitle(),
                                dto.getContent(), dto.getSpecies(), dto.getCategoryCode()));
                    }
                }
            } catch (Exception e) {
                log.warn("关键词检索失败 term={}: {}", term, e.getMessage());
            }
        }
        // 去重（同一碎片多词命中只计一次，保留第一个来源）
        Map<Long, MilvusVectorStore.Hit> distinct = new LinkedHashMap<>();
        for (MilvusVectorStore.Hit h : hits) {
            distinct.putIfAbsent(h.id(), h);
        }
        List<MilvusVectorStore.Hit> result = new ArrayList<>(distinct.values());
        return result.size() > KEYWORD_TOP_K ? result.subList(0, KEYWORD_TOP_K) : result;
    }

    /**
     * 按名次累加 RRF 分数：1/(k + rank)，rank 从 1 开始
     */
    private void addRanked(List<MilvusVectorStore.Hit> hits,
                           Map<Long, Double> scoreMap,
                           Map<Long, MilvusVectorStore.Hit> hitMap) {
        int rank = 1;
        for (MilvusVectorStore.Hit h : hits) {
            scoreMap.merge(h.id(), 1.0 / (RRF_K + rank), Double::sum);
            hitMap.putIfAbsent(h.id(), h);
            rank++;
        }
    }

    /**
     * 按相似度阈值过滤向量检索结果。
     * <p>
     * 为什么必须过滤：Milvus 的 topK 检索【永远】会返回最像的 K 条，即使一条都不相关。
     * 过滤后剩下的碎片会重新从第 1 名开始参与 RRF，也就是"只在相关结果之间排名次"。
     * <p>
     * 注意：只过滤向量路。关键词路是字面精确匹配（术语能命中才返回），本身精度较高，不适用相似度阈值。
     */
    private List<MilvusVectorStore.Hit> filterBySimilarity(List<MilvusVectorStore.Hit> hits) {
        Double threshold = milvusProperties.getSimilarityThreshold();
        if (threshold == null || threshold <= 0) {
            logScores(hits, null, hits.size());   // 未配置阈值：不过滤，但照样打印分数
            return hits;
        }
        List<MilvusVectorStore.Hit> kept = hits.stream()
                .filter(h -> h.score() != null && h.score() >= threshold)
                .toList();
        logScores(hits, threshold, kept.size());
        return kept;
    }

    /**
     * 打印本次向量检索的命中分数 —— 标定阈值就看这行日志。
     */
    private void logScores(List<MilvusVectorStore.Hit> hits, Double threshold, int keptSize) {
        if (hits.isEmpty()) {
            log.info("向量检索：0 条命中");
            return;
        }
        String scores = hits.stream()
                .map(h -> String.format("%.3f", h.score() == null ? -1f : h.score()))
                .collect(Collectors.joining(", "));
        if (threshold == null) {
            log.info("向量检索：命中 {} 条，分数 [{}]（未启用阈值过滤）", hits.size(), scores);
        } else {
            log.info("向量检索：命中 {} 条，阈值 {}，过滤后 {} 条，分数 [{}]",
                    hits.size(), threshold, keptSize, scores);
        }
    }

    /**
     * 问题里提到猫/狗时自动确定物种（species 未显式指定时）
     */
    private Integer detectSpecies(String question) {
        if (question == null) {
            return null;
        }
        boolean dog = question.contains("狗") || question.contains("犬");
        boolean cat = question.contains("猫");
        if (dog && !cat) {
            return 2;
        }
        if (cat && !dog) {
            return 1;
        }
        return null;
    }
}