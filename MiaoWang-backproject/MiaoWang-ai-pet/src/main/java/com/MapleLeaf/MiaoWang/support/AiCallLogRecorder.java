package com.MapleLeaf.MiaoWang.support;

import com.MapleLeaf.MiaoWang.config.LlmProperties;
import com.MapleLeaf.MiaoWang.dao.entity.AiCallLogDO;
import com.MapleLeaf.MiaoWang.dao.mapper.AiCallLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * AI 调用日志记录器（写入与 ai-knowledge 共用的 {@code miaowang_ai_call_log}，biz 固定 {@code chat}）
 *
 * <h3>为什么失败的调用也要记</h3>
 * 生成失败时消息表里没有对应行，但 token 可能已经烧掉了（模型报了错、
 * 或者内容被安全策略拦下返回空），成本与失败率必须算得进去 ——
 * 只记成功会让两者被系统性低估。
 *
 * <h3>为什么不让日志失败影响业务</h3>
 * 这里的每一次写都是 try/catch 兜住的：日志是"观测手段"，不是业务的一部分，
 * 绝不能因为日志表锁了、连接池满了就让用户聊不成天（真实故障里这类连带失败很常见）。
 *
 * <p>当前是<b>同步写</b>：一次 insert 几毫秒，不值得为它引入线程池与队列。
 * 等调用量上来（或要做实时统计）再改成 ai-knowledge 那种"异步 + 队列满降级同步"的写法。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCallLogRecorder {

    /** 场景：主人发起的一问一答 */
    public static final String SCENE_PET_CHAT = "pet_chat";

    /** 场景：打开页面时的自动问候 */
    public static final String SCENE_PET_GREET = "pet_greet";

    /** 业务标识，与 vip 的 VipBizTypeEnum.CHAT 对齐 */
    private static final String BIZ_CHAT = "chat";

    /** 百万 token 的换算基数（单价按"元/百万 token"配置） */
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);

    /** 错误消息入库前截断长度（保持与表结构 varchar(255) 一致的余量） */
    private static final int ERROR_MESSAGE_MAX = 240;

    private final AiCallLogMapper aiCallLogMapper;
    private final LlmProperties llmProperties;

    /**
     * 记一条成功日志
     *
     * @param usage 可能为 null（模型没返回用量），此时 token 与成本列留空，不记 0
     */
    public void recordSuccess(String scene, Long uid, Long conversationId, Long messageId,
                              Usage usage, long latencyMs) {
        AiCallLogDO row = base(scene, uid, conversationId, messageId, latencyMs);
        row.setSuccess(1);
        fillUsage(row, usage);
        insert(row);
    }

    /**
     * 记一条失败日志
     */
    public void recordFailure(String scene, Long uid, Long conversationId, String errorCode,
                              String errorMessage, long latencyMs) {
        AiCallLogDO row = base(scene, uid, conversationId, null, latencyMs);
        row.setSuccess(0);
        row.setErrorCode(errorCode);
        row.setErrorMessage(truncate(errorMessage));
        insert(row);
    }

    private AiCallLogDO base(String scene, Long uid, Long conversationId, Long messageId, long latencyMs) {
        AiCallLogDO row = new AiCallLogDO();
        row.setUid(uid);
        row.setBiz(BIZ_CHAT);
        row.setScene(scene);
        row.setConversationId(conversationId);
        row.setMessageId(messageId);
        row.setModel(modelName());
        row.setLatencyMs((int) Math.min(Math.max(latencyMs, 0), Integer.MAX_VALUE));
        return row;
    }

    /**
     * token 与估算成本。成本需要 Nacos 配了单价才算得出来；
     * 没配就留 NULL —— 统计时显示"未配置单价"，绝不编一个数字出来。
     */
    private void fillUsage(AiCallLogDO row, Usage usage) {
        if (usage == null) {
            return;
        }
        Integer tokenIn = usage.getPromptTokens();
        Integer tokenOut = usage.getCompletionTokens();
        row.setTokenIn(tokenIn);
        row.setTokenOut(tokenOut);
        if (tokenIn != null && tokenOut != null) {
            row.setTotalToken(tokenIn + tokenOut);
        }
        row.setEstimatedCost(estimateCost(tokenIn, tokenOut));
    }

    private BigDecimal estimateCost(Integer tokenIn, Integer tokenOut) {
        LlmProperties.ChatProperties chat = chatProperties();
        if (chat == null) {
            return null;
        }
        BigDecimal inPrice = chat.getPriceInputPerMillion();
        BigDecimal outPrice = chat.getPriceOutputPerMillion();
        if (inPrice == null && outPrice == null) {
            return null;
        }
        BigDecimal cost = BigDecimal.ZERO;
        if (inPrice != null && tokenIn != null) {
            cost = cost.add(inPrice.multiply(BigDecimal.valueOf(tokenIn)));
        }
        if (outPrice != null && tokenOut != null) {
            cost = cost.add(outPrice.multiply(BigDecimal.valueOf(tokenOut)));
        }
        return cost.divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    private String modelName() {
        LlmProperties.ChatProperties chat = chatProperties();
        return chat == null ? null : chat.getModel();
    }

    private LlmProperties.ChatProperties chatProperties() {
        LlmProperties.LlmModelItem dashscope = llmProperties.getDashscope();
        return dashscope == null ? null : dashscope.getChat();
    }

    private void insert(AiCallLogDO row) {
        try {
            aiCallLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("AI 调用日志写入失败（不影响本次业务）：scene={}, uid={}, err={}",
                    row.getScene(), row.getUid(), e.getMessage());
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MESSAGE_MAX ? message : message.substring(0, ERROR_MESSAGE_MAX);
    }
}