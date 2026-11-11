package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipPetBenefitRespDTO;

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
     * 查询「某一只宠物」的宠物级权益（供 ai-pet 等业务判断能否启动投递）
     *
     * <p>刻意与上面的用户级总览分成两个接口：宠物级权益是 per-pet 的，
     * 并进用户级总览会被合并成一个数字，业务侧就没法回答「这只宠物到底有没有被纪念」。
     */
    VipPetBenefitRespDTO getPetBenefitByUid(Long uid, Long pid);

    /**
     * 查询当前登录用户某只宠物的宠物级权益（前端宠物详情页用）
     */
    VipPetBenefitRespDTO getMyPetBenefit(Long pid);

    /**
     * 一键开通/续费（内部 = 下单 + 模拟支付），供联调与演示；真实支付用 {@code /vip/order} + 渠道回调
     */
    VipOrderRespDTO subscribe(VipSubscribeReqDTO requestParam);
}