package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dao.entity.AiCallLogDO;
import com.MapleLeaf.MiaoWang.dao.mapper.AiCallLogMapper;
import com.MapleLeaf.MiaoWang.dto.resp.AiCallStatRespDTO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * AI 调用日志统计服务（成本 / 用量 / 成功率）
 *
 * <h3>为什么聚合放在数据库侧</h3>
 * 日志表会随调用量线性增长（每次问答一行）。把几万行拉回应用层再求和，
 * 既浪费网络又占内存 —— 所以用 SQL 的 GROUP BY + SUM 聚合，Java 只做换算与拼装。
 *
 * <h3>为什么用 selectMaps 而不是写 XML</h3>
 * MyBatis-Plus 的 QueryWrapper 支持 select 聚合表达式，聚合照样发生在数据库侧，
 * 又不必手写 XML（省掉 {@code >=} 这类 XML 转义坑）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCallLogService {

    /**
     * 统计窗口上限：防止有人传 days=100000 把整表扫一遍
     */
    private static final int MAX_DAYS = 90;
    private static final int DEFAULT_DAYS = 7;

    private final AiCallLogMapper aiCallLogMapper;

    /**
     * 统计最近 {@code days} 天的调用量 / token / 成本 / 成功率。
     *
     * @param uid  只统计该用户；传 null 表示全站（内部运维用）
     * @param biz  业务过滤：rag / index；null 或空表示全部
     * @param days 统计天数（1~90，越界自动收敛）
     */
    public AiCallStatRespDTO stat(Long uid, String biz, Integer days) {
        int span = days == null ? DEFAULT_DAYS : Math.min(Math.max(days, 1), MAX_DAYS);
        // 起点取"span 天前的 00:00"，这样包含今天共 span 天
        Date from = Date.from(LocalDate.now()
                .minusDays(span - 1L)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant());

        QueryWrapper<AiCallLogDO> wrapper = new QueryWrapper<>();
        wrapper.select(
                        "DATE_FORMAT(create_time, '%Y-%m-%d') AS statDate",
                        "COUNT(*) AS calls",
                        "SUM(CASE WHEN success = 1 THEN 1 ELSE 0 END) AS successCalls",
                        "SUM(COALESCE(total_token, 0)) AS totalTokens",
                        "SUM(estimated_cost) AS totalCost",
                        "SUM(COALESCE(latency_ms, 0)) AS totalLatency")
                .eq("del_flag", 0)
                .ge("create_time", from);
        if (uid != null) {
            wrapper.eq("uid", uid);
        }
        if (StringUtils.hasText(biz)) {
            wrapper.eq("biz", biz);
        }
        wrapper.groupBy("statDate").orderByDesc("statDate");

        List<Map<String, Object>> rows = aiCallLogMapper.selectMaps(wrapper);
        return assemble(span, biz, rows);
    }

    /**
     * 把按天聚合的结果拼成返回体，同时累加出总量。
     */
    private AiCallStatRespDTO assemble(int span, String biz, List<Map<String, Object>> rows) {
        List<AiCallStatRespDTO.DailyRow> daily = new ArrayList<>(rows.size());
        long calls = 0;
        long success = 0;
        long tokens = 0;
        long latency = 0;
        BigDecimal cost = null;   // 全天都没有单价配置时保持 null（不编 0）

        for (Map<String, Object> row : rows) {
            AiCallStatRespDTO.DailyRow day = new AiCallStatRespDTO.DailyRow();
            day.setStatDate(str(row.get("statDate")));
            day.setCalls(num(row.get("calls")));
            day.setSuccessCalls(num(row.get("successCalls")));
            day.setTotalTokens(num(row.get("totalTokens")));
            day.setTotalCost(decimal(row.get("totalCost")));

            long dayCalls = nz(day.getCalls());
            long dayLatency = nz(num(row.get("totalLatency")));
            day.setAvgLatencyMs(avg(dayLatency, dayCalls));

            daily.add(day);
            calls += dayCalls;
            success += nz(day.getSuccessCalls());
            tokens += nz(day.getTotalTokens());
            latency += dayLatency;
            if (day.getTotalCost() != null) {
                cost = (cost == null ? BigDecimal.ZERO : cost).add(day.getTotalCost());
            }
        }

        AiCallStatRespDTO result = new AiCallStatRespDTO();
        result.setDays(span);
        result.setBiz(biz);
        result.setTotalCalls(calls);
        result.setSuccessCalls(success);
        result.setFailedCalls(calls - success);
        result.setSuccessRate(rate(success, calls));
        result.setTotalTokens(tokens);
        result.setTotalCost(cost == null ? null : cost.setScale(6, RoundingMode.HALF_UP));
        result.setAvgLatencyMs(avg(latency, calls));
        result.setDaily(daily);
        return result;
    }

    /**
     * 成功率：保留一位小数的百分比；没有调用时不显示 0%（会误导成"全失败"）
     */
    private static String rate(long success, long calls) {
        if (calls <= 0) {
            return null;
        }
        return BigDecimal.valueOf(success * 100.0 / calls)
                .setScale(1, RoundingMode.HALF_UP) + "%";
    }

    private static Integer avg(long total, long count) {
        if (count <= 0) {
            return null;
        }
        return (int) (total / count);
    }

    private static Long num(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}