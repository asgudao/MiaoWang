package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.AiPetChatReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.AiPetGreetReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiPetChatRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiPetGreetRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.AiPetMessageRespDTO;
import com.MapleLeaf.MiaoWang.service.AiPetChatService;
import com.MapleLeaf.MiaoWang.service.AiPetConversationService;
import com.MapleLeaf.MiaoWang.support.CurrentUid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 电子宠物接口：和它聊天、打开页面时它主动打招呼、回放你们聊过什么。
 *
 * <pre>
 *   POST /ai/pet/chat             主人先说，宠物回（扣「宠物聊天」次数）
 *   POST /ai/pet/greet            打开宠物页面时宠物主动开口（不扣次数，一个时段最多生成一次）
 *   GET  /ai/pet/message          回放与这只宠物的历史消息
 * </pre>
 *
 * <p><b>全部需要登录</b>：宠物档案、聊天次数、会话记忆都是按账号走的，没有 uid 什么都做不了，
 * 所以也没有"游客模式"这回事。网关白名单没有、也不该有这几个路径
 * （白名单只放读路径，见 miaowang-api-contract）。
 *
 * <p><b>身份只认请求头</b>：uid 一律由 common 的 {@code UserTransmitFilter} 从
 * {@code username + token} 还原，请求体里不接受 uid —— 结构上杜绝"聊别人的宠物"。
 * 传进来的 {@code pid} 也会经 pet 服务核对归属（不属于自己的一律按"宠物不存在"处理）。
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/ai/pet")
@RequiredArgsConstructor
public class AiPetController {

    private final AiPetChatService aiPetChatService;
    private final AiPetConversationService aiPetConversationService;
    private final CurrentUid currentUid;

    /**
     * 和宠物聊天
     *
     * @param requestParam {@code { pid?, message }}；pid 不传就用第一只宠物
     */
    @PostMapping("/chat")
    public Result<AiPetChatRespDTO> chat(@RequestBody AiPetChatReqDTO requestParam) {
        Long uid = currentUid.require();
        return Results.success(aiPetChatService.chat(uid, requestParam.getPid(), requestParam.getMessage()));
    }

    /**
     * 打开宠物页面时的自动问候
     *
     * <p>为什么是 POST：缓存未命中时它会真的调一次大模型（有副作用），
     * 也不该被浏览器或中间层当成幂等读缓存起来。
     *
     * <p>请求体可以整个不传（{@code {} } 或空 body），语义是"我来了"。
     * 生成失败不会报错，只回一个 {@code greeting = null}，前端不显示问候气泡即可 ——
     * 首页不能因为大模型抖动而变成错误页。
     */
    @PostMapping("/greet")
    public Result<AiPetGreetRespDTO> greet(@RequestBody(required = false) AiPetGreetReqDTO requestParam) {
        Long uid = currentUid.require();
        Long pid = requestParam == null ? null : requestParam.getPid();
        return Results.success(aiPetChatService.greet(uid, pid));
    }

    /**
     * 回放与这只宠物的历史消息（时间正序，最近 {@code limit} 条；默认 50、上限 200）
     *
     * <p>还没聊过时返回空数组，不是错误 —— 前端据此显示"还没跟它说过话"的引导态。
     *
     * <p>⚠️ {@code pid} 不传时的口径与 chat / greet <b>不同</b>：
     * 这里取「最近聊过的那只宠物」，而 chat / greet 取「第一只宠物」。
     * 原因见 {@code AiPetConversationService#history} 的注释（历史回放只查库，不必为此多调一次 pet 服务）。
     * 前端进入具体某只宠物的对话页时，请一律带上 {@code pid}，就没有歧义。
     */
    @GetMapping("/message")
    public Result<List<AiPetMessageRespDTO>> messages(@RequestParam(value = "pid", required = false) Long pid,
                                                      @RequestParam(value = "limit", required = false) Integer limit) {
        Long uid = currentUid.require();
        return Results.success(aiPetConversationService.history(uid, pid, limit));
    }
}