package com.MapleLeaf.MiaoWang.rag;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.config.LlmProperties;
import com.MapleLeaf.MiaoWang.config.MilvusProperties;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;
import com.MapleLeaf.MiaoWang.openfegin.knowledge.KnowledgeServiceFeign;
import com.MapleLeaf.MiaoWang.rag.milvus.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量索引构建（俗称"灌库"）：把知识库里的知识碎片加工成向量，写进 Milvus。
 *
 * <h3>一、先澄清一个最容易混的概念：分片(chunk) vs 批次(batch)</h3>
 * <pre>
 *   分片(chunk)：把一篇长文档切成若干小段，每段单独算一个向量。
 *                → 本项目【没有做分片】。因为知识库里的碎片本身就是人工写好的短条目
 *                  （来自 sql/knowledge-seed.sql，例如"尾巴竖直、尾尖微微向前弯：心情愉悦、友好问候"），
 *                  一条碎片通常只有 20~50 字，直接就是天然的 chunk。
 *                → 以后若要接入 PDF/长文章，才需要引入分片（spring-ai 自带 TokenTextSplitter）。
 *
 *   批次(batch)：一次 HTTP 请求带几条碎片去调 embedding 接口。
 *                → 就是下面的 BATCH_SIZE = 10，这个 10 是百炼接口的限制，和"分片"是两回事。
 * </pre>
 *
 * <h3>二、一条碎片是怎么变成一行 Milvus 记录的</h3>
 * <pre>
 *   MySQL: knowledge_fragment 表（409 条）
 *        │  ① Feign 调 knowledge 服务，拿全量"已发布"碎片
 *        ▼
 *   一条碎片 = {id=285, title=睡觉时打呼噜, content=..., species=1, categoryCode=behavior}
 *        │  ② embedText()：拼成"标题：正文"这一整段文字
 *        ▼
 *   "睡觉时打呼噜：……"  →③ embedding 模型 → float[1024]（1024 个小数，代表这段话的语义）
 *        │  ④ toRow()：碎片信息 + 向量 → 一行 Map
 *        ▼
 *   Milvus 集合 miao_wang_knowledge_fragment（1 条碎片 = 1 行 = 1 个向量）
 * </pre>
 *
 * <h3>三、触发方式</h3>
 * 手动：POST /mapleleaf/miaowang/v1/ai/rag/index/build（前端那个「重建知识索引」按钮）
 * 升级方向：知识发布时发消息 → 本服务消费 → 只灌变化的那几条（增量同步）
 */
@Slf4j
@Service
public class RagIndexService {

    /**
     * 单次 embedding 接口能带几条碎片（注意：这是"批次大小"，不是分片大小）。
     * 百炼/MaaS 限制每次最多 10 条，超过会报：
     * 400 InvalidParameter: batch size is invalid, it should not be larger than 10.
     */
    private static final int BATCH_SIZE = 10;

    private final MilvusVectorStore milvusVectorStore;
    private final EmbeddingModel embeddingModel;
    private final KnowledgeServiceFeign knowledgeServiceFeign;
    private final LlmProperties llmProperties;
    private final MilvusProperties milvusProperties;

    public RagIndexService(MilvusVectorStore milvusVectorStore,
                           EmbeddingModel embeddingModel,
                           KnowledgeServiceFeign knowledgeServiceFeign,
                           LlmProperties llmProperties,
                           MilvusProperties milvusProperties) {
        this.milvusVectorStore = milvusVectorStore;
        this.embeddingModel = embeddingModel;
        this.knowledgeServiceFeign = knowledgeServiceFeign;
        this.llmProperties = llmProperties;
        this.milvusProperties = milvusProperties;
    }

    /**
     * 重建向量索引（全量：把当前知识库的全部碎片重新灌一遍，旧的先清空）。
     * <p>
     * 为什么用全量而不是增量？碎片可能被修改、被下架，全量重灌最简单也最不容易出错。
     * 409 条碎片 = 41 次 embedding 请求，大约十几秒到几十秒。
     *
     * @return 实际灌入的碎片条数（前端就显示"共灌入 N 条"）
     */
    public int rebuildIndex() {
        // ① 通过 Feign 找 knowledge 服务要全量"已发布"碎片（status=2 的那些）
        Result<List<KnowledgeFragmentRespDTO>> result = knowledgeServiceFeign.listFragments(null, null);
        List<KnowledgeFragmentRespDTO> fragments = result == null || result.getData() == null
                ? List.of() : result.getData();
        if (fragments.isEmpty()) {
            log.warn("知识库没有已发布碎片，跳过灌库");
            return 0;
        }
        int dimension = resolveDimension();
        log.info("开始重建向量索引：{} 条碎片，维度 {}，集合 {}", fragments.size(), dimension,
                milvusProperties.getCollectionName());

        // ② 分批向量化：每 10 条碎片打成一个包调一次 embedding 接口。
        //    先把全部向量算完再落库 —— 这样中途失败也不会把 Milvus 里已有的索引清空。
        List<Map<String, Object>> rows = new ArrayList<>(fragments.size());
        for (int i = 0; i < fragments.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, fragments.size());
            List<KnowledgeFragmentRespDTO> batch = fragments.subList(i, end);
            List<String> texts = batch.stream().map(this::embedText).toList();  // 碎片 → 送模型的文字
            List<float[]> vectors = embeddingModel.embed(texts);                // 文字 → 向量（一次返回 10 个）
            for (int j = 0; j < batch.size(); j++) {
                rows.add(toRow(batch.get(j), vectors.get(j)));                  // 碎片 + 向量 → 一行记录
            }
            log.info("已向量化 {}/{} 条", end, fragments.size());
        }
        // ③ 全部成功后才有动 Milvus：先清空旧数据，再批量写入
        milvusVectorStore.deleteAll(dimension);
        milvusVectorStore.upsert(rows, dimension);
        log.info("向量索引重建完成，共灌入 {} 条", rows.size());
        return rows.size();
    }

    /**
     * 一条知识碎片 + 它的向量 → 一行 Milvus 记录。
     * 这里的 key（id/title/content/species/categoryCode/embedding）必须和
     * MilvusVectorStore.ensureCollection() 里定义的字段名一模一样，否则写入会报字段不存在。
     */
    private Map<String, Object> toRow(KnowledgeFragmentRespDTO dto, float[] vector) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", dto.getId());
        row.put("title", nullToEmpty(dto.getTitle()));
        row.put("content", nullToEmpty(dto.getContent()));
        row.put("species", dto.getSpecies() == null ? 0L : dto.getSpecies().longValue());
        row.put("categoryCode", nullToEmpty(dto.getCategoryCode()));
        row.put("embedding", toFloatList(vector));
        return row;
    }

    /**
     * 向量维度必须三处一致，否则写入 Milvus 会失败：
     * embedding 模型输出维度 = Milvus 集合维度 = 这里返回的 dimension。
     * 优先取 embedding 配置里的 dimensions（Nacos: llm.dashscope.embedding.dimensions = 1024），
     * 没配就退回 milvus.dimension（同样 1024）。
     */
    private int resolveDimension() {
        LlmProperties.LlmSubProperties embedding = llmProperties.getDashscope() == null
                ? null : llmProperties.getDashscope().getEmbedding();
        if (embedding != null && embedding.getDimensions() != null) {
            return embedding.getDimensions();
        }
        return milvusProperties.getDimension();
    }

    /**
     * 决定"拿碎片的哪部分文字去算向量"。
     * 这里把【标题 + 正文】拼起来一起算：标题往往信息量很大（如"睡觉时打呼噜"），
     * 只算正文会丢掉这部分语义，检索时就不容易命中。
     * 标题和正文都为空时给个占位串，避免把空字符串送进 embedding 接口。
     */
    private String embedText(KnowledgeFragmentRespDTO dto) {
        String text = nullToEmpty(dto.getTitle()) + "：" + nullToEmpty(dto.getContent());
        return text.isBlank() ? "未知内容" : text;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) {
            list.add(v);
        }
        return list;
    }
}