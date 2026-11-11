package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipPetBenefitRespDTO;
import com.MapleLeaf.MiaoWang.service.UserVipService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的会员接口
 * <ul>
 *   <li>GET  /benefit         我的权益总览（含宠物数量上限与今日剩余次数）</li>
 *   <li>GET  /benefit/pet/{pid} 我的某只宠物的<b>宠物级</b>权益（含未购买的 false 情形）</li>
 *   <li>POST /subscribe       一键开通/续费（内部 = 下单 + 模拟支付，联调与演示用）</li>
 * </ul>
 *
 * <p>订单的完整读写（下单、模拟支付、退款、订单列表）都在 {@link VipOrderController}，
 * 统一挂在 {@code /vip/order} 下（订单列表的 URL 没变，前端不用改）。
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
     * 我某只宠物的宠物级权益：没买过也正常返回（petVip=false），前端不用写两套逻辑
     *
     * <p>和用户级总览分开，是为了让「用户开了月卡但没给这只宠物买纪念包」这种情况可表达。
     */
    @GetMapping("/benefit/pet/{pid}")
    public Result<VipPetBenefitRespDTO> getMyPetBenefit(@PathVariable("pid") Long pid) {
        return Results.success(userVipService.getMyPetBenefit(pid));
    }

    /**
     * 一键开通/续费会员（同档位续费自动在原到期时间上叠加）
     *
     * <p>内部走「下单 + 模拟支付」两个步骤，最终效果和以前一致；
     * 想走真实支付流程请用 {@code POST /vip/order} + 渠道回调。
     */
    @PostMapping("/subscribe")
    public Result<VipOrderRespDTO> subscribe(@RequestBody VipSubscribeReqDTO requestParam) {
        return Results.success(userVipService.subscribe(requestParam));
    }
}