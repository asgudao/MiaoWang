package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.AiCallStatRespDTO;
import com.MapleLeaf.MiaoWang.service.AiCallLogService;
import com.MapleLeaf.MiaoWang.support.RagQuotaGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 调用日志 / 成本统计接口
 *
 * <p><b>为什么只统计自己</b>：uid 从登录态还原，不接受前端传 uid ——
 * 否则任何人填别人的 uid 就能看到对方的用量与花费（越权）。
 * 全站统计属于运维视角，应该走内部接口或后台管理，不在对外接口上开口子。
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/ai/log")
@RequiredArgsConstructor
public class AiCallLogController {

    private final AiCallLogService aiCallLogService;
    private final RagQuotaGuard ragQuotaGuard;

    /**
     * 我的 AI 调用统计（调用量 / 成功率 / token / 估算成本）
     *
     * @param days 统计天数，默认 7，最大 90
     * @param biz  业务过滤：rag / index；不传=全部
     */
    @GetMapping("/stat")
    public Result<AiCallStatRespDTO> stat(@RequestParam(required = false) Integer days,
                                          @RequestParam(required = false) String biz) {
        Long uid = ragQuotaGuard.currentUid();
        return Results.success(aiCallLogService.stat(uid, biz, days));
    }
}