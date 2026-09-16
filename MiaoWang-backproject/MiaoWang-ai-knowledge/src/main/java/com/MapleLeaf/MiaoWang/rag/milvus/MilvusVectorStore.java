package com.MapleLeaf.MiaoWang.rag.milvus;

import com.MapleLeaf.MiaoWang.config.MilvusProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量数据库操作封装 —— 本项目所有"向量读写"都集中在这一个文件里。
 *
 * <h3>一、这个类在 RAG 流程里的位置</h3>
 * <pre>
 *   写（灌库）：RagIndexService    → 调本类 ensureCollection() / deleteAll() / upsert()
 *   读（检索）：KnowledgeRetriever → 调本类 search()
 *   其它类一律不直接碰 Milvus SDK，Milvus 的所有细节都被关在这个文件里
 * </pre>
 *
 * <h3>二、Milvus 概念 vs MySQL（对照着看最好懂）</h3>
 * <pre>
 *   Milvus          MySQL        本项目对应
 *   ------------    ----------   ------------------------------------------------
 *   collection      表           miao_wang_knowledge_fragment（知识碎片）
 *   field           列           id / title / content / species / categoryCode / embedding
 *   entity(实体)    行           一条知识碎片 = 一个向量
 *   index           索引         embedding 字段上的 AUTOINDEX（专门给向量用的索引）
 *   load            无对应概念   把集合加载进内存后才能被检索（必须调，最容易漏）
 * </pre>
 *
 * <h3>三、集合结构（一条记录 = 一条知识碎片）</h3>
 * <pre>
 *   id           Int64            主键，直接复用 MySQL 里 knowledge_fragment.id
 *                                 —— 重复灌库时按主键覆盖，天然幂等，不会灌出重复数据
 *   title        VarChar(256)     标题，如"尾巴竖直、尾尖微微向前弯"
 *   content      VarChar(65535)   正文，如"心情愉悦、友好问候、表示信任"
 *   species      Int64            1=猫 2=狗（检索时可按物种过滤）
 *   categoryCode VarChar(64)      diet/behavior/care/health
 *   embedding    FloatVector(1024) 正文的语义向量，相似度比较就发生在这一列
 * </pre>
 *
 * <h3>四、为什么用官方 Java SDK 直连，而不用 Spring AI 的 Milvus starter</h3>
 * 老师 demo 用的是 spring-ai-starter-vector-store-milvus（配置写在 spring.ai.vectorstore.milvus.*）。
 * 本项目手写 SDK 调用：好处是能讲清每一步到底发了什么请求、建了什么索引，面试时更好说原理；
 * 代价就是建集合 / 建索引 / load 都要自己管 —— 这些活都在本类里。
 *
 * @see com.MapleLeaf.MiaoWang.rag.RagIndexService 写入方（灌库）
 * @see KnowledgeRetriever 读取方（检索）
 */
@Slf4j
@Component
public class MilvusVectorStore {

    /** Milvus 相关配置（地址/集合名/维度/topK），来自 Nacos 里的 milvus.* */
    private final MilvusProperties milvusProperties;

    /** 懒加载的 Milvus 客户端；volatile 配合 getClient() 里的双重检查锁使用 */
    private volatile MilvusClientV2 client;

    public MilvusVectorStore(MilvusProperties milvusProperties) {
        this.milvusProperties = milvusProperties;
    }

    /**
     * 一条向量检索结果 = 一条知识碎片 + 相似度分数。
     * 字段就是集合里的那几列；后面 KnowledgeRetriever 会把它转成 RetrievableFragment 参与 RRF 融合排序。
     */
    public record Hit(Long id, Float score, String title, String content, Integer species, String categoryCode) {
    }

    // ==================== 集合管理 ====================

    /**
     * 集合不存在则创建：建表 → 建索引 → load，三步缺一不可。
     * <p>
     * 为什么必须 load？集合刚创建时数据还在磁盘上，必须 loadCollection 加载进内存，
     * 之后 search 才能查到数据 —— 这是最容易漏的一步。
     */
    public synchronized void ensureCollection(int dimension) {
        MilvusClientV2 c = getClient();
        String name = milvusProperties.getCollectionName();
        // ① 已经存在就直接返回（本方法可以反复调用，幂等）
        if (Boolean.TRUE.equals(c.hasCollection(HasCollectionReq.builder().collectionName(name).build()))) {
            return;
        }
        // ② 定义表结构：5 个普通字段 + 1 个向量字段
        CreateCollectionReq.CollectionSchema schema = c.createSchema();
        // isPrimaryKey(true) = 主键；不设 autoID，表示主键由我们传入（= 碎片 id）
        schema.addField(AddFieldReq.builder().fieldName("id").dataType(DataType.Int64).isPrimaryKey(true).build());
        // VarChar 必须指定 maxLength，否则 Milvus 直接报错
        schema.addField(AddFieldReq.builder().fieldName("title").dataType(DataType.VarChar).maxLength(256).build());
        schema.addField(AddFieldReq.builder().fieldName("content").dataType(DataType.VarChar).maxLength(65535).build());
        schema.addField(AddFieldReq.builder().fieldName("species").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder().fieldName("categoryCode").dataType(DataType.VarChar).maxLength(64).build());
        // 向量字段：dimension 必须和 embedding 模型的输出维度一致（本项目 1024），不一致写入就会失败
        schema.addField(AddFieldReq.builder().fieldName("embedding").dataType(DataType.FloatVector).dimension(dimension).build());
        c.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .collectionSchema(schema)
                .build());
        // ③ 给向量字段建索引：AUTOINDEX = 让 Milvus 自己挑算法；
        //    COSINE = 余弦相似度，衡量两段文本的"语义接近程度"（文本向量最常用）
        c.createIndex(CreateIndexReq.builder()
                .collectionName(name)
                .indexParams(List.of(IndexParam.builder()
                        .fieldName("embedding")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.COSINE)
                        .build()))
                .build());
        // ④ 加载进内存，之后才能被 search 检索到
        c.loadCollection(LoadCollectionReq.builder().collectionName(name).build());
        log.info("Milvus 集合已创建: {}, 维度 {}", name, dimension);
    }

    // ==================== 写入 ====================

    /**
     * 批量写入（upsert = insert or update：有则覆盖，无则新增）。
     * 主键是知识碎片 id，所以同一条碎片重复灌库只会覆盖旧行，不会产生重复数据。
     *
     * @param rows 行数据，key 必须与集合字段名一致，embedding 是 List&lt;Float&gt;（1024 个数）
     */
    public void upsert(List<Map<String, Object>> rows, int dimension) {
        MilvusClientV2 c = getClient();
        ensureCollection(dimension);   // 第一次写入时自动建表
        // Milvus SDK 要求数据用 gson 的 JsonObject 传，所以先把 Map 转一层（见 toJsonObject）
        List<JsonObject> data = rows.stream().map(this::toJsonObject).toList();
        // 单次请求最多带 64 条，超出就拆成多次发
        // （注意：这个 64 是 Milvus 的限制，和 embedding 那边"单次最多 10 条"是两回事）
        for (int i = 0; i < data.size(); i += 64) {
            c.upsert(UpsertReq.builder()
                    .collectionName(milvusProperties.getCollectionName())
                    .data(data.subList(i, Math.min(i + 64, data.size())))
                    .build());
        }
        log.info("Milvus upsert {} 条", rows.size());
    }

    /**
     * 清空集合里的所有数据（重建索引前调用）。
     * filter("id > 0") 就是"选中全部行"——Milvus 删除靠表达式筛选，没有 truncate 那种语句。
     */
    public void deleteAll(int dimension) {
        MilvusClientV2 c = getClient();
        ensureCollection(dimension);
        c.delete(DeleteReq.builder()
                .collectionName(milvusProperties.getCollectionName())
                .filter("id > 0")
                .build());
        log.info("Milvus 集合已清空");
    }

    // ==================== 检索 ====================

    /**
     * 向量相似度检索：把问题向量丢进去，拿回最像的 topK 条知识碎片。
     *
     * @param queryVector 问题向量（维度必须与集合一致，都是 1024）
     * @param topK        返回几条（本项目配 10 条，之后交 RRF 融合排序取前 5）
     * @param species     按物种过滤：1=猫 2=狗；传 null 表示不过滤
     */
    public List<Hit> search(float[] queryVector, int topK, Integer species) {
        MilvusClientV2 c = getClient();
        // filter 是 Milvus 的过滤表达式，作用等于 SQL 里的 WHERE species = 1
        String filter = species == null ? null : "species == " + species;
        SearchReq request = SearchReq.builder()
                .collectionName(milvusProperties.getCollectionName())
                .data(List.of(new FloatVec(queryVector)))   // 要查的向量（可传多个，这里一次问一个问题）
                .annsField("embedding")                     // 在哪个向量字段上比相似度
                .topK(topK)                                 // 返回最像的前 K 条
                .metricType(IndexParam.MetricType.COSINE)   // 相似度算法，必须和建索引时一致
                .outputFields(List.of("id", "title", "content", "species", "categoryCode"))  // 除分数外还要带回哪些字段
                .filter(filter)
                .build();
        SearchResp resp = c.search(request);
        // 返回结果是"两层"的：外层对应每个查询向量，内层才是命中的若干条。
        // 我们只传了 1 个查询向量，所以外层只有 1 组，但仍按两层遍历（SDK 的固定结构）。
        List<Hit> hits = new ArrayList<>();
        for (List<SearchResp.SearchResult> group : resp.getSearchResults()) {
            for (SearchResp.SearchResult r : group) {
                Map<String, Object> entity = r.getEntity();   // entity = 这一行的字段值（outputFields 里的那些）
                hits.add(new Hit(
                        toLong(entity.get("id")),
                        r.getScore(),                          // COSINE 相似度，越大越相似
                        toStringValue(entity.get("title")),
                        toStringValue(entity.get("content")),
                        entity.get("species") == null ? null : toLong(entity.get("species")).intValue(),
                        toStringValue(entity.get("categoryCode"))));
            }
        }
        return hits;
    }

    // ==================== 私有 ====================

    /**
     * 懒加载 Milvus 客户端，全应用共用一个（双重检查锁保证线程安全）。
     * 为什么不写在构造方法里？因为构造方法抛异常会让整个应用启动失败，
     * 而 Milvus 挂了不应该导致 ai-knowledge 起不来 —— 放到第一次真正用到时再连。
     */
    private MilvusClientV2 getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new MilvusClientV2(ConnectConfig.builder()
                            .uri(milvusProperties.getUri())
                            .build());
                    log.info("Milvus 客户端已创建: {}", milvusProperties.getUri());
                }
            }
        }
        return client;
    }

    /**
     * Java Map → gson JsonObject：Milvus SDK 的写入接口要求传 JsonObject。
     * 按值的实际类型分别处理：null / 数字 / 字符串 / List（向量就是 List&lt;Float&gt;，转成 JSON 数组）。
     */
    private JsonObject toJsonObject(Map<String, Object> row) {
        JsonObject obj = new JsonObject();
        row.forEach((k, v) -> {
            if (v == null) {
                obj.add(k, JsonNull.INSTANCE);
            } else if (v instanceof Number n) {
                obj.add(k, new JsonPrimitive(n));
            } else if (v instanceof String s) {
                obj.add(k, new JsonPrimitive(s));
            } else if (v instanceof List<?> list) {
                JsonArray arr = new JsonArray();
                for (Object e : list) {
                    arr.add(new JsonPrimitive(((Number) e).floatValue()));
                }
                obj.add(k, arr);
            } else {
                obj.add(k, new JsonPrimitive(String.valueOf(v)));
            }
        });
        return obj;
    }

    private static Long toLong(Object o) {
        return o == null ? null : ((Number) o).longValue();
    }

    private static String toStringValue(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}