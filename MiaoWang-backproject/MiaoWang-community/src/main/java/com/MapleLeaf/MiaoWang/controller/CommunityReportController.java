package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.ReportCreateReqDTO;
import com.MapleLeaf.MiaoWang.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区举报接口
 * <p>
 * 举报只落库（状态=待处理），不做自动下架：没有管理员角色之前，
 * 任何"自动判定"都是把权限判断写错。
 */
@RestController
@RequiredArgsConstructor
public class CommunityReportController {

    private final ReportService reportService;

    /**
     * 举报帖子或评论
     */
    @PostMapping("/mapleleaf/miaowang/v1/community/report")
    public Result<Long> report(@RequestBody ReportCreateReqDTO requestParam) {
        return Results.success(reportService.createReport(requestParam));
    }
}