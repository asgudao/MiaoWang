package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;

import java.util.List;

/**
 * 用户会员服务
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
     * 开通/续费会员（当前为模拟支付：下单即生效）
     */
    VipOrderRespDTO subscribe(VipSubscribeReqDTO requestParam);

    /**
     * 当前登录用户的订阅订单列表
     */
    List<VipOrderRespDTO> listMyOrders();
}