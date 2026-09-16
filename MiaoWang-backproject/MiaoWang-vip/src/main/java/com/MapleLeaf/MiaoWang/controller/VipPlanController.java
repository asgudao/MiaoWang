package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.VipPlanRespDTO;
import com.MapleLeaf.MiaoWang.service.VipPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员档位接口
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/plan")
@RequiredArgsConstructor
public class VipPlanController {

    private final VipPlanService vipPlanService;

    /**
     * 可购买的档位列表（前端会员页渲染价格与权益，免费档不返回）
     */
    @GetMapping
    public Result<List<VipPlanRespDTO>> listPlans() {
        return Results.success(vipPlanService.listPurchasablePlans());
    }
}