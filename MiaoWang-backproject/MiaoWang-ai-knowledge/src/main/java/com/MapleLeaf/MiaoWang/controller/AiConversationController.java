package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.AiMessageFeedbackReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiConversationRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiMessageRespDTO;
import com.MapleLeaf.MiaoWang.service.AiConversationService;
import com.MapleLeaf.MiaoWang.support.CurrentUid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 会话 / 消息 / 反馈接口。
 *
 * <p>会话不需要"新建"接口：提问时不带 conversationId 就自动开新会话（见 AiConversationService）。
 *
 * <p>所有接口都要登录，并且只能操作<b>自己的</b>会话：uid 从登录态还原，
 * 不信任前端传的 uid（否则填别人的 id 就能看别人的聊天记录）。
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/ai/conversation")
@RequiredArgsConstructor
public class AiConversationController {

    private final AiConversationService aiConversationService;
    private final CurrentUid currentUid;

    /**
     * 历史会话列表（按最后活跃时间倒序）
     *
     * @param limit 最多返回几条，默认 20，上限 100
     */
    @GetMapping("/list")
    public Result<List<AiConversationRespDTO>> list(@RequestParam(required = false) Integer limit) {
        return Results.success(aiConversationService.list(currentUid.require(), limit));
    }

    /**
     * 某个会话的消息历史（时间正序，用于回放）
     *
     * @param limit 最多返回最近几条，默认 50，上限 200
     */
    @GetMapping("/{conversationId}/message")
    public Result<List<AiMessageRespDTO>> history(@PathVariable Long conversationId,
                                                  @RequestParam(required = false) Integer limit) {
        return Results.success(aiConversationService.history(currentUid.require(), conversationId, limit));
    }

    /**
     * 删除会话（会话与它下面的消息一起软删）
     */
    @DeleteMapping("/{conversationId}")
    public Result<Void> delete(@PathVariable Long conversationId) {
        aiConversationService.delete(currentUid.require(), conversationId);
        return Results.success();
    }

    /**
     * 给某条 AI 回答点 👍/👎
     * <p>
     * 重新生成不需要单独的接口：前端把同一个问题带上同一个 conversationId 重新调 /ai/rag/ask 即可，
     * 新回答作为新的一轮追加，旧的留在历史里可以被对比。
     */
    @PostMapping("/message/{messageId}/feedback")
    public Result<Void> feedback(@PathVariable Long messageId,
                                 @RequestBody AiMessageFeedbackReqDTO requestParam) {
        aiConversationService.feedback(currentUid.require(), messageId, requestParam.getType());
        return Results.success();
    }
}