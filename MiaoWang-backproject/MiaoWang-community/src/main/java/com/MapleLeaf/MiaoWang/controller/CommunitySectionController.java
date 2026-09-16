package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.SectionRespDTO;
import com.MapleLeaf.MiaoWang.service.SectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 社区版块接口
 * <p>
 * 公开内容：只读版块列表，不校验身份。
 */
@RestController
@RequiredArgsConstructor
public class CommunitySectionController {

    private final SectionService sectionService;

    /**
     * 社区版块列表（宠物美照 / 宠物知识 / 幼宠专区 / 品种鉴定 / 病症鉴定 / 活动专区）
     */
    @GetMapping("/mapleleaf/miaowang/v1/community/section")
    public Result<List<SectionRespDTO>> listSections() {
        return Results.success(sectionService.listEnabledSections());
    }
}