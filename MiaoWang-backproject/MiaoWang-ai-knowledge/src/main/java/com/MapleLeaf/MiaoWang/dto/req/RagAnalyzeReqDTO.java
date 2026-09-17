package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

/**
 * 服务间 AI 解读请求（healthy 调用 {@code /ai/rag/internal/analyze}）
 *
 * <p>{@code uid} 只用于调用日志的成本归因，<b>不作为鉴权依据</b> ——
 * 这个接口本来就没有登录态，能调它的只有内网服务。
 */
@Data
public class RagAnalyzeReqDTO {

    /**
     * 用户问题（由健康模块根据症状记录拼好）
     */
    private String question;

    /**
     * 物种过滤：1=猫 2=狗，不传则从问题中自动识别
     */
    private Integer species;

    /**
     * 发起解读的用户ID（仅用于日志归因，可为空）
     */
    private Long uid;
}