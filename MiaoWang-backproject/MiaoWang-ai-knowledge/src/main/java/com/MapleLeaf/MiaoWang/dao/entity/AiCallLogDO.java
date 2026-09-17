package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 调用日志（一次大模型/embedding 调用 = 一行）
 * <p>
 * 与 {@link AiMessageDO} 的分工：消息表存"问答内容"，日志表存"调用过程"。
 * 失败的调用（模型超时、网关报错、检索异常）没有消息行，但必须出现在日志里，
 * 否则失败率与成本会被系统性低估 —— 这就是两者不能合并的原因。
 * <p>
 * 本表 append-only：只插入、不修改、不软删（del_flag 恒为 0）。
 */
@Data
@TableName("miaowang_ai_call_log")
@EqualsAndHashCode(callSuper = false)
public class AiCallLogDO extends BaseDO {

    /**
     * 日志ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（未登录/额度拒绝时可能为空）
     */
    private Long uid;

    /**
     * 业务：rag / chat / index
     */
    private String biz;

    /**
     * 场景：ask / ask_stream / index_build
     */
    private String scene;

    /**
     * 关联会话（可空）
     */
    private Long conversationId;

    /**
     * 关联 AI 消息（可空）
     */
    private Long messageId;

    /**
     * 模型名，如 qwen3.7-flash
     */
    private String model;

    /**
     * 是否成功：1=成功 0=失败
     */
    private Integer success;

    /**
     * 失败时的业务错误码
     */
    private String errorCode;

    /**
     * 失败原因（截断保存）
     */
    private String errorMessage;

    /**
     * 输入 token
     */
    private Integer tokenIn;

    /**
     * 输出 token
     */
    private Integer tokenOut;

    /**
     * 总 token（冗余，聚合统计时少算一次加法）
     */
    private Integer totalToken;

    /**
     * 耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 估算成本（元）。只有配置了单价才计算，否则留 NULL —— 统计接口宁可显示"未知"也不猜数
     */
    private BigDecimal estimatedCost;
}