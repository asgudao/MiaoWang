package com.MapleLeaf.MiaoWang.openfegin.vip;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.req.VipQuotaConsumeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaFeignRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 会员额度 Feign
 *
 * <p>额度规则（每天几次、每月几次、会员多少）全部由 vip 模块判定，本模块只消费结果，
 * 不自己判断会员状态 —— 否则改一次权益要改 N 个服务。
 *
 * <p>走的是 vip 的 {@code /vip/internal/**}（服务间接口，无登录态），
 * 所以 uid 放在请求体里显式传，不依赖请求头透传。
 *
 * <p>⚠️ <b>contextId 不能删</b>：common 里的 {@code VipServiceFeign} 也是
 * {@code @FeignClient("miaowang-vip")}，而 ai-knowledge 的 classpath 上两个都在。
 * 同一服务名的两个客户端若都不带 contextId，FeignClientSpecification 的 bean 名会撞车
 * （{@code miaowang-vip.FeignClientSpecification} 重复定义）→ <b>ai-knowledge 启动直接失败</b>。
 * 规则：<b>同一服务名的第二个 Feign 客户端必须声明自己的 contextId</b>。
 */
@FeignClient(name = "miaowang-vip", contextId = "vipQuotaFeign")
public interface VipQuotaFeign {

    /**
     * 扣减一次额度：allowed=false 表示次数已用完，调用方应直接拒绝本次业务动作
     */
    @PostMapping("/mapleleaf/miaowang/v1/vip/internal/quota/consume")
    Result<VipQuotaFeignRespDTO> consume(@RequestBody VipQuotaConsumeReqDTO requestParam);

    /**
     * 回退一次额度：业务最终失败时调用（生成失败不扣次数）
     */
    @PostMapping("/mapleleaf/miaowang/v1/vip/internal/quota/refund")
    Result<Void> refund(@RequestBody VipQuotaConsumeReqDTO requestParam);
}