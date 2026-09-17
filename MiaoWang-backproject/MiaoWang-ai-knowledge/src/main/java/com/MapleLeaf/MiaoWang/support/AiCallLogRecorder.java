package com.MapleLeaf.MiaoWang.support;

import com.MapleLeaf.MiaoWang.config.LlmProperties;
import com.MapleLeaf.MiaoWang.dao.entity.AiCallLogDO;
import com.MapleLeaf.MiaoWang.dao.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AI 调用日志记录器 —— 每次调用大模型都留一条"过程记录"，供成本统计与排障使用。
 *
 * <h3>一、为什么异步</h3>
 * 写日志对用户没有任何价值（用户只关心答案），却要等一次 DB 往返。
 * 所以这里用 {@code @Async("aiLogExecutor")} 丢到独立线程池，
 * 问答链路的耗时里不含写日志的开销。
 *
 * <h3>二、为什么失败也要记</h3>
 * 失败的调用（模型超时、网关报错、检索异常）在消息表里没有对应行，
 * 若只记成功的，失败率与成本都会被系统性低估 —— 这是日志表独立于消息表的根本原因。
 *
 * <h3>三、成本怎么算</h3>
 * 成本 = 输入 token × 输入单价 + 输出 token × 输出单价（单价为「元/百万 token」），
 * 单价放在 Nacos 的 {@code llm.dashscope.chat.price-input-per-million / price-output-per-million}。
 * <b>没配单价就不算</b>，estimated_cost 留 NULL，统计接口显示"未配置" —— 宁可缺数据也不编数字。
 *
 * @see com.MapleLeaf.MiaoWang.service.AiCallLogService 统计查询
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallLogRecorder {

    /**
     * 业务标识，与 vip 额度服务的 biz 保持一致（rag）
     */
    public static final String BIZ_RAG = "rag";

    /**
     * 向量化/索引业务
     */
    public static final String BIZ_INDEX = "index";

    public static final String SCENE_ASK = "ask";
    public static final String SCENE_ASK_STREAM = "ask_stream";
    public static final String SCENE_INDEX_BUILD = "index_build";

    /**
     * AI 健康解读（healthy 的服务间调用）。虽然不扣 RAG 额度，但同样是真金白银的大模型调用，
     * 所以要和问答一样进成本统计 —— 否则"解读花了多少钱"事后无从查起。
     */
    public static final String SCENE_AI_ANALYZE = "analyze";

    /**
     * 单价单位是「元/百万 token」，换算时要除它
     */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /**
     * error_message 列长 255，截断时留点余量
     */
    private static final int ERROR_MESSAGE_MAX = 250;

    private final AiCallLogMapper aiCallLogMapper;
    private final LlmProperties llmProperties;

    /**
     * 记录一次成功调用（含 token 用量）。
     *
     * @param usage Spring AI 的用量对象；<b>允许为 null</b>（embedding 不返回用量，
     *              流式也偶有拿不到 usage 的情况），此时 token 列为 NULL，统计时会被跳过
     * @param conversationId 关联会话（可空）
     * @param messageId      关联的 AI 消息（可空）
     */
    @Async("aiLogExecutor")
    public void recordSuccess(String scene, Long uid, Usage usage, long latencyMs,
                              Long conversationId, Long messageId) {
        AiCallLogDO row = base(BIZ_RAG, scene, uid, latencyMs);
        row.setConversationId(conversationId);
        row.setMessageId(messageId);
        row.setSuccess(1);
        row.setModel(chatModelName());
        fillUsage(row, usage);
        save(row);
    }

    /**
     * 记录一次失败调用（含错误码与原因）。
     *
     * @param conversationId 关联会话（可空：失败时可能还没建出会话）
     */
    @Async("aiLogExecutor")
    public void recordFailure(String scene, Long uid, String errorCode, String errorMessage, long latencyMs,
                              Long conversationId) {
        AiCallLogDO row = base(BIZ_RAG, scene, uid, latencyMs);
        row.setConversationId(conversationId);
        row.setSuccess(0);
        row.setModel(chatModelName());
        row.setErrorCode(truncate(errorCode, 32));
        row.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX));
        save(row);
    }

    /**
     * 兼容重载：没有会话/消息可关联的调用（如 healthy 的服务间解读）直接用这个，
     * 等价于把关联字段传 null。
     * <p>这个重载也标了 {@code @Async}：否则外部调用会先在请求线程同步跑，
     * 再"委托"给下面那个方法（类内自调用不走代理，不会二次异步），等于白异步。
     */
    @Async("aiLogExecutor")
    public void recordSuccess(String scene, Long uid, Usage usage, long latencyMs) {
        recordSuccess(scene, uid, usage, latencyMs, null, null);
    }

    /**
     * 兼容重载：失败 + 不关联会话
     */
    @Async("aiLogExecutor")
    public void recordFailure(String scene, Long uid, String errorCode, String errorMessage, long latencyMs) {
        recordFailure(scene, uid, errorCode, errorMessage, latencyMs, null);
    }

    /**
     * 记录一次索引重建（批量 embedding 调用）。
     * embedding 接口不返回 token 用量，所以这里只记耗时与成败，token 列为 NULL ——
     * 将来若要算向量化成本，需要在 RagIndexService 里按字符数估算后传进来。
     *
     * @param errorMessage 为 null 表示成功
     */
    @Async("aiLogExecutor")
    public void recordIndexBuild(Long uid, long latencyMs, String errorMessage) {
        AiCallLogDO row = base(BIZ_INDEX, SCENE_INDEX_BUILD, uid, latencyMs);
        row.setSuccess(errorMessage == null ? 1 : 0);
        row.setModel(embeddingModelName());
        row.setErrorMessage(truncate(errorMessage, ERROR_MESSAGE_MAX));
        save(row);
    }

    private AiCallLogDO base(String biz, String scene, Long uid, long latencyMs) {
        AiCallLogDO row = new AiCallLogDO();
        row.setBiz(biz);
        row.setScene(scene);
        row.setUid(uid);
        row.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        return row;
    }

    private void fillUsage(AiCallLogDO row, Usage usage) {
        if (usage == null) {
            return;
        }
        Integer in = usage.getPromptTokens();
        Integer out = usage.getCompletionTokens();
        row.setTokenIn(in);
        row.setTokenOut(out);
        if (in != null || out != null) {
            row.setTotalToken((in == null ? 0 : in) + (out == null ? 0 : out));
        }
        row.setEstimatedCost(estimateCost(in, out));
    }

    /**
     * 估算成本（元）。两个单价都没配置时返回 null。
     */
    private BigDecimal estimateCost(Integer tokenIn, Integer tokenOut) {
        LlmProperties.LlmSubProperties chat = chatProps();
        BigDecimal priceIn = chat == null ? null : chat.getPriceInputPerMillion();
        BigDecimal priceOut = chat == null ? null : chat.getPriceOutputPerMillion();
        if (priceIn == null && priceOut == null) {
            return null;
        }
        BigDecimal cost = BigDecimal.ZERO;
        if (tokenIn != null && priceIn != null) {
            cost = cost.add(priceIn.multiply(BigDecimal.valueOf(tokenIn)));
        }
        if (tokenOut != null && priceOut != null) {
            cost = cost.add(priceOut.multiply(BigDecimal.valueOf(tokenOut)));
        }
        return cost.divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    /**
     * 真正落库。异常只记日志不外抛：日志写失败绝不能影响主流程
     * （何况这里已经在异步线程里，抛出去也没人接）。
     */
    private void save(AiCallLogDO row) {
        try {
            aiCallLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("AI 调用日志写入失败，scene={}：{}", row.getScene(), e.getMessage());
        }
    }

    /**
     * 模型名取自配置（Nacos llm.dashscope.chat.model）。
     * 不从响应里取，是因为失败时根本没有响应可读。
     */
    private String chatModelName() {
        LlmProperties.LlmSubProperties chat = chatProps();
        return chat == null ? null : truncate(chat.getModel(), 64);
    }

    private String embeddingModelName() {
        LlmProperties.LlmModelItem dashscope = llmProperties.getDashscope();
        LlmProperties.LlmSubProperties embedding = dashscope == null ? null : dashscope.getEmbedding();
        return embedding == null ? null : truncate(embedding.getModel(), 64);
    }

    private LlmProperties.LlmSubProperties chatProps() {
        LlmProperties.LlmModelItem dashscope = llmProperties.getDashscope();
        return dashscope == null ? null : dashscope.getChat();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}