package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.HealthAiAnalyzeRespDTO;
import com.MapleLeaf.MiaoWang.service.HealthAiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 症状解读（会员功能）
 *
 * <p>为什么是 POST 而不是 GET：它每次都会真的调大模型（有成本、结果也不可缓存复用），
 * 不属于"可安全重放的读操作"。同时它不修改任何数据，所以失败也不回滚什么。
 *
 * <p>权限与计费口径：
 * <ul>
 *   <li>必须登录，且 {@code pid} 必须是当前用户的宠物（否则 `B000400`）</li>
 *   <li>必须是有 AI 解读权限的档位（34.9 及以上，否则 `B000411`）</li>
 *   <li><b>不消耗 RAG 问答配额</b>：它用的是「AI 症状解读」这个档位功能</li>
 * </ul>
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/health")
@RequiredArgsConstructor
public class HealthAiAnalysisController {

    private final HealthAiAnalysisService healthAiAnalysisService;

    /**
     * 对某只宠物做一次 AI 症状解读（基于该宠物最近的症状记录 + 知识库）
     */
    @PostMapping("/{pid}/ai-analysis")
    public Result<HealthAiAnalyzeRespDTO> analyze(@PathVariable("pid") Long pid) {
        return Results.success(healthAiAnalysisService.analyze(pid));
    }
}