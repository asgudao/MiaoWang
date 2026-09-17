package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.VipPromoRedeemReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;

/**
 * 会员促销（兑换码 / 优惠码）
 *
 * <p>只有「送会员时长」的兑换码需要显式核销；「下单立减」的码在下单时传
 * {@code promoCode}，由订单流程在支付成功时自动核销（见 {@code VipOrderServiceImpl}）。
 */
public interface VipPromoService {

    /**
     * 核销兑换码，成功即获得对应档位的会员时长
     *
     * @return 生成的 0 元促销订单（前端可据此刷新权益）
     */
    VipOrderRespDTO redeem(VipPromoRedeemReqDTO requestParam);
}