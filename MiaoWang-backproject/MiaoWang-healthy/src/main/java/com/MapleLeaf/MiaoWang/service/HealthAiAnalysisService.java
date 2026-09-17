package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.resp.HealthAiAnalyzeRespDTO;

/**
 * AI 症状解读
 *
 * <p>会员功能（34.9 及以上档位的 {@code healthAiAnalysis}）：把宠物最近的症状记录
 * 拼成问题，交给 ai-knowledge 走"知识库检索 + 大模型生成"，返回辅助参考与知识库依据。
 */
public interface HealthAiAnalysisService {

    /**
     * 对某只宠物做一次 AI 症状解读
     *
     * @param pid 宠物ID（必须是当前登录用户的宠物）
     */
    HealthAiAnalyzeRespDTO analyze(Long pid);
}