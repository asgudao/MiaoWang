package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;

/**
 * 用户会员服务
 *
 * <p>订单相关（下单/支付/退款/订单列表）在 {@code VipOrderService}；
 * 本接口只负责权益总览与兼容用的「一键开通」。
 */
public interface UserVipService {

    /**
     * 查询当前登录用户的权益总览（含今日剩余次数）
     */
    UserVipBenefitRespDTO getMyBenefit();

    /**
     * 按用户ID查询权益总览（供其他服务调用）
     */
    UserVipBenefitRespDTO getBenefitByUid(Long uid);

    /**
     * 一键开通/续费（内部 = 下单 + 模拟支付），供联调与演示；真实支付用 {@code /vip/order} + 渠道回调
     */
    VipOrderRespDTO subscribe(VipSubscribeReqDTO requestParam);
}