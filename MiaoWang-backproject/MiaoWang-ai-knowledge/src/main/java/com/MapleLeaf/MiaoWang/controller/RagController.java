package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.RagAskReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.RagAskRespDTO;
import com.MapleLeaf.MiaoWang.rag.RagAskService;
import com.MapleLeaf.MiaoWang.rag.RagIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * RAG 相关接口：
 * <ul>
 *   <li>/ai/rag/ask          宠物健康问答（非流式，一次性返回 answer + sources）</li>
 *   <li>/ai/rag/ask/stream   宠物健康问答（SSE 流式：先推 sources 事件，再逐段推 message 事件）</li>
 *   <li>/ai/rag/index/build  重建向量索引（把知识库已发布碎片灌入 Milvus）</li>
 * </ul>
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/ai/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagAskService ragAskService;
    private final RagIndexService ragIndexService;

    @PostMapping("/ask")
    public Result<RagAskRespDTO> ask(@RequestBody RagAskReqDTO requestParam) {
        validate(requestParam);
        return Results.success(ragAskService.ask(requestParam));
    }

    /**
     * SSE 流式问答：
     * <pre>
     * event: sources   → JSON 数组（引用的知识碎片，可先渲染"正在检索到的资料"）
     * event: message   → 每生成一段就推一次（前端逐段追加，打字机效果）
     * [流结束]
     * </pre>
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody RagAskReqDTO requestParam) {
        validate(requestParam);
        SseEmitter emitter = new SseEmitter(0L); // 0 = 不超时
        try {
            // ① 混合检索 + 拼上下文（同步完成，先拿 sources）
            RagAskService.AskContext context = ragAskService.buildContext(requestParam);
            emitter.send(SseEmitter.event().name("sources").data(context.sources()));
            // ② 订阅流式生成，逐段推送，事件处理线程异步执行，不阻塞请求线程
            ragAskService.streamAnswer(context)
                    .doOnNext(response -> {
                        // 只推有文字的帧：多模态/思考模型的流里有大量无 text 的帧
                        // （思考过程、role、usage、结束帧），实测 268 帧里 244 帧为空。
                        // 注意：这里判 null/空串而不是 hasText，避免把换行、空格这类排版帧丢掉。
                        String text = extractText(response);
                        if (text == null || text.isEmpty()) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event().name("message").data(text));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnComplete(emitter::complete)
                    .doOnError(emitter::completeWithError)
                    .subscribe();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @PostMapping("/index/build")
    public Result<Integer> rebuildIndex() {
        return Results.success(ragIndexService.rebuildIndex());
    }

    private void validate(RagAskReqDTO requestParam) {
        if (requestParam.getQuestion() == null || requestParam.getQuestion().isBlank()) {
            throw new ClientException("问题不能为空");
        }
    }

    /**
     * 安全取出增量文本：流里存在没有 text 的帧（思考过程 / usage / 结束帧），
     * 这些帧的 result / output 也可能为 null，直接取值会 NPE 打断整个流。
     */
    private static String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }
}