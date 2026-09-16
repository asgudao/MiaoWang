package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.VipQuotaConsumeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;
import com.MapleLeaf.MiaoWang.service.VipQuotaService;
import com.MapleLeaf.MiaoWang.support.VipUserHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 每日额度接口（当前登录用户）
 * <ul>
 *   <li>GET  /query?biz=rag    查剩余次数</li>
 *   <li>POST /consume          扣一次次数（执行业务动作前调用）</li>
 * </ul>
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/quota")
@RequiredArgsConstructor
public class VipQuotaController {

    private final VipQuotaService vipQuotaService;

    /**
     * 查询今日额度
     *
     * @param biz 业务类型：rag（知识问答）/ chat（宠物聊天）
     */
    @GetMapping("/query")
    public Result<VipQuotaRespDTO> queryQuota(@RequestParam("biz") String biz) {
        return Results.success(vipQuotaService.queryQuota(VipUserHelper.currentUid(), biz));
    }

    /**
     * 扣减一次额度，返回 allowed=false 表示今日次数已用完（不是系统错误，前端据此弹开通会员引导）
     */
    @PostMapping("/consume")
    public Result<VipQuotaRespDTO> consumeQuota(@RequestBody VipQuotaConsumeReqDTO requestParam) {
        return Results.success(vipQuotaService.consumeQuota(VipUserHelper.currentUid(), requestParam.getBiz()));
    }
}