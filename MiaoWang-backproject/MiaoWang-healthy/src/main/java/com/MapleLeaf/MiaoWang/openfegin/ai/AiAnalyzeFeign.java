package com.MapleLeaf.MiaoWang.openfegin.ai;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.req.AiRagAskReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiRagAskRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 解读 Feign（调用 ai-knowledge 的服务间接口）
 *
 * <p>走的是 {@code /ai/rag/internal/analyze}：<b>不传登录头、不扣 RAG 额度</b>。
 * 门禁（能不能用 AI 解读）由本模块按 VIP 的 {@code healthAiAnalysis} 把关，
 * ai-knowledge 只负责"检索 + 生成"。
 *
 * <p>因为不需要透传登录头，所以这里不挂 {@code FeignHeaderPropagationConfig}。
 */
@FeignClient("miaowang-ai-knowledge")
public interface AiAnalyzeFeign {

    /**
     * 按拼好的问题做知识库检索 + 大模型解读
     */
    @PostMapping("/mapleleaf/miaowang/v1/ai/rag/internal/analyze")
    Result<AiRagAskRespDTO> analyze(@RequestBody AiRagAskReqDTO requestParam);
}