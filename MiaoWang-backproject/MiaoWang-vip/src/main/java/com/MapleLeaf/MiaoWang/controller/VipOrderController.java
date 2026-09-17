package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.VipOrderCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipRefundReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.service.VipOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会员订单接口（下单 / 支付 / 退款 / 列表）
 *
 * <ul>
 *   <li>POST /order                   下单：只创建「待支付」订单，<b>不发会员</b></li>
 *   <li>GET  /order                   我的订单列表（顺手关掉超时未支付的单）</li>
 *   <li>POST /order/{orderNo}/pay     模拟支付（联调用；走的是和真实回调完全相同的链路）</li>
 *   <li>POST /order/{orderNo}/refund  退款（窗口期内自助，退款成功会回收会员时长）</li>
 * </ul>
 *
 * <p>真实支付流程：前端 {@code POST /vip/order} 拿到支付参数 → 用户付款 →
 * 渠道回调 {@code POST /vip/pay/notify/{channel}} → 订单转「已支付」并发放会员 →
 * 前端轮询订单详情或靠长连接感知成功。
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/order")
@RequiredArgsConstructor
public class VipOrderController {

    private final VipOrderService vipOrderService;

    /**
     * 下单（幂等：同一 requestId 只会出一单；同一档位有未超时的待支付单会直接复用）
     */
    @PostMapping
    public Result<VipOrderRespDTO> createOrder(@RequestBody VipOrderCreateReqDTO requestParam) {
        return Results.success(vipOrderService.createOrder(requestParam));
    }

    /**
     * 我的订阅订单列表
     */
    @GetMapping
    public Result<List<VipOrderRespDTO>> listMyOrders() {
        return Results.success(vipOrderService.listMyOrders());
    }

    /**
     * 模拟支付：仅本人、仅待支付订单可用
     */
    @PostMapping("/{orderNo}/pay")
    public Result<VipOrderRespDTO> mockPay(@PathVariable("orderNo") String orderNo) {
        return Results.success(vipOrderService.mockPay(orderNo));
    }

    /**
     * 退款：仅本人、仅已支付、仅窗口期内；重复调用按幂等处理
     */
    @PostMapping("/{orderNo}/refund")
    public Result<VipOrderRespDTO> refund(@PathVariable("orderNo") String orderNo,
                                         @RequestBody(required = false) VipRefundReqDTO requestParam) {
        return Results.success(vipOrderService.refund(orderNo, requestParam));
    }
}