package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.Vip3dApplyReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.Vip3dOrderRespDTO;
import com.MapleLeaf.MiaoWang.service.Vip3dOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 3D 宠物模型定制接口（当前登录用户）
 * <ul>
 *   <li>POST /order                     提交定制申请</li>
 *   <li>GET  /order                     我的定制单列表</li>
 *   <li>GET  /order/{orderNo}           定制单详情</li>
 *   <li>GET  /order/{orderNo}/model     取定制模型地址（仅本人，首页展示用）</li>
 *   <li>POST /order/{orderNo}/cancel    取消定制</li>
 * </ul>
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/3d")
@RequiredArgsConstructor
public class Vip3dOrderController {

    private final Vip3dOrderService vip3dOrderService;

    /**
     * 提交 3D 模型定制申请（人人可申请，不属于会员专属权益）
     */
    @PostMapping("/order")
    public Result<Vip3dOrderRespDTO> apply(@RequestBody Vip3dApplyReqDTO requestParam) {
        return Results.success(vip3dOrderService.apply(requestParam));
    }

    /**
     * 我的定制单列表
     */
    @GetMapping("/order")
    public Result<List<Vip3dOrderRespDTO>> listMyOrders() {
        return Results.success(vip3dOrderService.listMyOrders());
    }

    /**
     * 定制单详情
     */
    @GetMapping("/order/{orderNo}")
    public Result<Vip3dOrderRespDTO> getMyOrder(@PathVariable("orderNo") String orderNo) {
        return Results.success(vip3dOrderService.getMyOrder(orderNo));
    }

    /**
     * 取定制模型地址：只有下单本人能取到（他人请求按订单不存在处理）
     */
    @GetMapping("/order/{orderNo}/model")
    public Result<String> getModelUrl(@PathVariable("orderNo") String orderNo) {
        return Results.success(vip3dOrderService.getModelUrl(orderNo));
    }

    /**
     * 取消定制（仅待处理/制作中可取消）
     */
    @PostMapping("/order/{orderNo}/cancel")
    public Result<Vip3dOrderRespDTO> cancel(@PathVariable("orderNo") String orderNo) {
        return Results.success(vip3dOrderService.cancel(orderNo));
    }
}