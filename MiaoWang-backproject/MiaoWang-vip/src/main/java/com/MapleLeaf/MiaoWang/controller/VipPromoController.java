package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.VipPromoRedeemReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.service.VipPromoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员促销接口
 *
 * <p>两类促销码，入口不同，别混：
 * <ul>
 *   <li><b>送会员时长（PLAN）</b>：{@code POST /vip/promo/redeem} 核销即到账</li>
 *   <li><b>下单立减（DISCOUNT）</b>：在 {@code POST /vip/order} 的 body 里带 {@code promoCode}，
 *       支付成功后才占名额（用码下单却不付款不会浪费名额）</li>
 * </ul>
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/promo")
@RequiredArgsConstructor
public class VipPromoController {

    private final VipPromoService vipPromoService;

    /**
     * 核销兑换码（送会员时长）
     */
    @PostMapping("/redeem")
    public Result<VipOrderRespDTO> redeem(@RequestBody VipPromoRedeemReqDTO requestParam) {
        return Results.success(vipPromoService.redeem(requestParam));
    }
}