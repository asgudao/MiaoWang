package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AI 问答会话（一个会话 = 一串连续的多轮对话）
 * <p>
 * 为什么把 message_count / last_message_time 冗余在这里：
 * 历史列表页要按"最后活跃时间"倒序、还要显示条数，若放在消息表里算，
 * 列表页每行都要一个 count 子查询 —— 冗余两个字段就把列表变成单表查询。
 */
@Data
@TableName("miaowang_ai_conversation")
@EqualsAndHashCode(callSuper = false)
public class AiConversationDO extends BaseDO {

    /**
     * 会话ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户
     */
    private Long uid;

    /**
     * 标题：取首条提问的前 20 字（列表页不拉正文）
     */
    private String title;

    /**
     * 消息条数（一问一答算 2 条）
     */
    private Integer messageCount;

    /**
     * 最后一条消息时间：列表排序依据
     */
    private Date lastMessageTime;
}