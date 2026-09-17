package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AI 问答消息（一问一答各一行）
 * <p>
 * sources_json 存的是那次回答引用的知识碎片（JSON 数组）。
 * 存下来的目的：回放历史时不用重新检索（省一次 embedding + 一次向量检索 + 一次关键词检索），
 * 而且用户当时看到的引用来源必须与历史一致 —— 知识库后来改了也不该篡改历史。
 */
@Data
@TableName("miaowang_ai_message")
@EqualsAndHashCode(callSuper = false)
public class AiMessageDO extends BaseDO {

    /**
     * 消息ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属会话
     */
    private Long conversationId;

    /**
     * 所属用户（冗余，反馈鉴权与按人查询都要用）
     */
    private Long uid;

    /**
     * 角色：1=用户提问 2=AI回答
     */
    private Integer role;

    /**
     * 消息正文
     */
    private String content;

    /**
     * AI 回答引用的知识碎片（JSON）
     */
    private String sourcesJson;

    /**
     * AI 行：是否命中知识库 0=否 1=是
     */
    private Integer hasKnowledge;

    /**
     * 输入 token
     */
    private Integer tokenIn;

    /**
     * 输出 token
     */
    private Integer tokenOut;

    /**
     * 本次生成耗时（毫秒）
     */
    private Integer latencyMs;

    /**
     * 答案反馈：NULL=未评价 1=👍 2=👎
     */
    private Integer feedback;

    /**
     * 反馈时间
     */
    private Date feedbackTime;
}