package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.Vip3dCompleteReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipInternalQuotaConsumeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.Vip3dOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;
import com.MapleLeaf.MiaoWang.service.UserVipService;
import com.MapleLeaf.MiaoWang.service.Vip3dOrderService;
import com.MapleLeaf.MiaoWang.service.VipQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务间调用接口（不面向浏览器）
 * <ul>
 *   <li>GET  /benefit/{uid}          查某用户权益（如宠物模块校验「还能不能再养一只」）</li>
 *   <li>POST /quota/consume          ai-knowledge / ai-pet 执行前扣额度</li>
 *   <li>POST /quota/refund           业务最终失败时回退额度（失败不扣次数）</li>
 *   <li>POST /3d/complete            制作方回填定制模型地址</li>
 * </ul>
 *
 * <p>安全提醒：这些接口没有登录态，必须在网关侧限制访问（只允许内网/其他服务调用），
 * 当前网关白名单与鉴权范围未覆盖此处，接入前请先加限制。
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/internal")
@RequiredArgsConstructor
public class VipInternalController {

    private final UserVipService userVipService;

    private final VipQuotaService vipQuotaService;

    private final Vip3dOrderService vip3dOrderService;

    /**
     * 查询指定用户权益（宠物数量上限 / RAG 次数 / 聊天次数）
     */
    @GetMapping("/benefit/{uid}")
    public Result<UserVipBenefitRespDTO> getBenefitByUid(@PathVariable("uid") Long uid) {
        return Results.success(userVipService.getBenefitByUid(uid));
    }

    /**
     * 扣减指定用户额度：返回 allowed=false 表示次数已用完，调用方应直接拒绝本次业务动作
     */
    @PostMapping("/quota/consume")
    public Result<VipQuotaRespDTO> consumeQuota(@RequestBody VipInternalQuotaConsumeReqDTO requestParam) {
        return Results.success(vipQuotaService.consumeQuota(requestParam.getUid(), requestParam.getBiz()));
    }

    /**
     * 制作方回填定制模型地址，订单置为已完成
     */
    @PostMapping("/3d/complete")
    public Result<Vip3dOrderRespDTO> complete(@RequestBody Vip3dCompleteReqDTO requestParam) {
        return Results.success(vip3dOrderService.complete(requestParam));
    }

    /**
     * 回退一次额度：调用方扣了额度但业务最终失败（如大模型报错）时回退，保证失败不扣用户次数
     */
    @PostMapping("/quota/refund")
    public Result<Void> refundQuota(@RequestBody VipInternalQuotaConsumeReqDTO requestParam) {
        vipQuotaService.refundQuota(requestParam.getUid(), requestParam.getBiz());
        return Results.success();
    }
}