package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 调用统计（成本/用量）返回体
 */
@Data
public class AiCallStatRespDTO {

    /**
     * 统计天数（回显请求参数）
     */
    private Integer days;

    /**
     * 统计业务：rag / index / null=全部
     */
    private String biz;

    /**
     * 总调用次数
     */
    private Long totalCalls;

    /**
     * 成功次数
     */
    private Long successCalls;

    /**
     * 失败次数
     */
    private Long failedCalls;

    /**
     * 成功率，形如 "98.5%"
     */
    private String successRate;

    /**
     * 总 token（输入+输出）
     */
    private Long totalTokens;

    /**
     * 估算总成本（元）。<b>null 表示还没配置单价</b>，前端应显示"未配置"而不是 0
     */
    private BigDecimal totalCost;

    /**
     * 平均耗时（毫秒）
     */
    private Integer avgLatencyMs;

    /**
     * 按天明细（倒序，最新在前）
     */
    private List<DailyRow> daily;

    /**
     * 单日聚合行
     */
    @Data
    public static class DailyRow {

        /**
         * 日期 yyyy-MM-dd
         */
        private String statDate;

        private Long calls;

        private Long successCalls;

        private Long totalTokens;

        /**
         * 当日估算成本；全天都没有单价配置时为 null
         */
        private BigDecimal totalCost;

        private Integer avgLatencyMs;
    }
}