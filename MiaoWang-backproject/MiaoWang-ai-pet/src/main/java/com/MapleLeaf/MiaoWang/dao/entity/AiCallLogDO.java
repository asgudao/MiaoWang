package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 调用日志（<b>与 ai-knowledge 共用同一张表</b> {@code miaowang_ai_call_log}）
 *
 * <h3>为什么两个模块共写一张表</h3>
 * 建表时就预留了 {@code biz} 这一列（注释写的是 {@code rag / chat / index}），
 * 也就是"宠物聊天"从一开始就被规划成这张日志的一种业务。共用带来的好处很直接：
 * <ul>
 *   <li>成本与失败率可以<b>跨模块一起统计</b>（"这个月 AI 一共花了多少"不用 union 两张表）</li>
 *   <li>项目是单库，两模块同表没有分库事务问题</li>
 * </ul>
 * 代价是"谁拥有这张表"变得模糊：本类只是<b>写侧的镜像</b>，
 * 表结构变更时 ai-knowledge 与 ai-pet 两边都要跟着改（已记入挂账）。
 *
 * <p>本表 append-only：只插入、不修改、不软删（del_flag 恒为 0）。
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
     * 用户ID
     */
    private Long uid;

    /**
     * 业务：ai-pet 固定写 chat（VipBizTypeEnum.CHAT）
     */
    private String biz;

    /**
     * 场景：pet_chat / pet_greet
     */
    private String scene;

    /**
     * 关联会话（可空）
     */
    private Long conversationId;

    /**
     * 关联宠物消息（可空：生成失败时没有消息行）
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
     * 总 token（冗余，聚合统计少算一次加法）
     */
    private Integer totalToken;

    /**
     * 耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 估算成本（元）。只有配置了单价才计算，否则留 NULL
     */
    private BigDecimal estimatedCost;
}