package com.MapleLeaf.MiaoWang.dto.resp;

import com.MapleLeaf.MiaoWang.dto.resp.RagSourceRespDTO;
import lombok.Data;

import java.util.List;

/**
 * RAG 问答响应
 */
@Data
public class RagAskRespDTO {

    /**
     * 大模型生成的回答
     */
    private String answer;

    /**
     * 是否命中知识库（false 表示没有检索到相关内容）
     */
    private Boolean hasKnowledge;

    /**
     * 回答依据的知识碎片来源（引用溯源）
     */
    private List<RagSourceRespDTO> sources;

    /**
     * 本次回答所属会话ID：前端下一次追问要把它带回来（多轮上下文靠它串起来）
     */
    private Long conversationId;

    /**
     * 本次 AI 回答的消息ID：前端的 👍/👎 要带它（为 null 表示本次回答没能落库）
     */
    private Long messageId;

    /**
     * 模型的思考过程（思考模型才有；非流式接口才返回，流式走 SSE 的 thinking 事件）。
     * Nacos 配 {@code llm.dashscope.chat.show-thinking=false} 时该字段为 null。
     */
    private String reasoning;
}