package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.service.UserVipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的会员接口
 * <ul>
 *   <li>GET  /benefit    我的权益总览（含宠物数量上限与今日剩余次数）</li>
 *   <li>POST /subscribe  开通/续费（当前为模拟支付，下单即生效）</li>
 *   <li>GET  /order      我的订阅订单</li>
 * </ul>
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip")
@RequiredArgsConstructor
public class UserVipController {

    private final UserVipService userVipService;

    /**
     * 我的权益总览：未开会员也正常返回免费档权益（vip=false），前端不用写两套逻辑
     */
    @GetMapping("/benefit")
    public Result<UserVipBenefitRespDTO> getMyBenefit() {
        return Results.success(userVipService.getMyBenefit());
    }

    /**
     * 开通/续费会员（同档位续费自动在原到期时间上叠加）
     */
    @PostMapping("/subscribe")
    public Result<VipOrderRespDTO> subscribe(@RequestBody VipSubscribeReqDTO requestParam) {
        return Results.success(userVipService.subscribe(requestParam));
    }

    /**
     * 我的订阅订单列表
     */
    @GetMapping("/order")
    public Result<List<VipOrderRespDTO>> listMyOrders() {
        return Results.success(userVipService.listMyOrders());
    }
}