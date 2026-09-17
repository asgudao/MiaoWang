package com.MapleLeaf.MiaoWang.openfegin.vip;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.VipBenefitFeignRespDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 会员服务 Feign
 *
 * <p>权益判定统一由 VIP 模块负责，业务模块只消费结果：
 * 健康档案能记多少条、能不能导出、能不能用 AI 解读，都由这个接口返回，
 * 业务模块不自己判断会员状态（否则规则会散落各处，改一次价格要改 N 个服务）。
 *
 * <p>这个接口的 uid 在路径里，不依赖登录头，所以不需要透传配置。
 */
@FeignClient("miaowang-vip")
public interface VipServiceFeign {

    /**
     * 按用户ID查询权益
     */
    @GetMapping("/mapleleaf/miaowang/v1/vip/internal/benefit/{uid}")
    Result<VipBenefitFeignRespDTO> getBenefitByUid(@PathVariable("uid") Long uid);
}