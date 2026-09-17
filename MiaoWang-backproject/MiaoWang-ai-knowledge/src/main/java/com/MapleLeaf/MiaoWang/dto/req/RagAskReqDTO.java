package com.MapleLeaf.MiaoWang.dto.req;

import lombok.Data;

/**
 * RAG 问答请求参数
 */
@Data
public class RagAskReqDTO {

    /**
     * 用户问题
     */
    private String question;

    /**
     * 物种过滤：1=猫 2=狗，不传则从问题中自动识别
     */
    private Integer species;

    /**
     * 会话ID：不传 = 开启一个新会话；传了 = 在这个会话里继续追问（多轮上下文）
     */
    private Long conversationId;
}