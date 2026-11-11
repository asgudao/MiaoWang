package com.MapleLeaf.MiaoWang.support;


import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.exception.ServiceException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.openfegin.pet.PetServiceFeign;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 宠物归属校验（3D 定制 / 宠物级会员下单用）
 *
 * <p>两处调用方都要求「这个 pid 必须是当前登录用户自己的宠物」：
 * <ul>
 *   <li>3D 定制单：不校验的话用户能拿别人的 pid 下单，把别人的宠物信息写进自己的定制单</li>
 *   <li>宠物级（per-pet）会员下单：不校验的话能给别人家的宠物买纪念包，
 *       等于替别人的宠物"锁"了一个纪念位（而权益发到的是自己的 uid 上，谁也拿不到）</li>
 * </ul>
 *
 * <p>实现思路与 healthy 的 {@code PetOwnershipChecker} 保持一致：<b>不让 pet 模块新增内部接口</b>，
 * 直接复用它的「查我的宠物」——该接口按登录态返回当前用户的宠物列表，
 * 所以目标 pid 不在列表里就说明无权访问；登录头由 common 的
 * {@code FeignHeaderPropagationConfig} 透传过去。
 *
 * <p>两条硬规则：
 * <ul>
 *   <li>pet 服务不可用时<b>报错不放行</b> —— 归属校验失败还放行等于开了后门</li>
 *   <li>不区分「宠物不存在」和「不是你的宠物」，避免用报错差异探测别人有哪些宠物ID</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VipPetOwnershipChecker {

    private final PetServiceFeign petServiceFeign;

    /**
     * pid 为空表示「暂不绑定宠物档案」，直接通过；给了 pid 就必须是自己的宠物
     *
     * @param pid 宠物ID，可为空
     * @return 命中的宠物（调用方可顺手取档案里的真实名字，防止前端传错或伪造）；未绑定宠物时返回 null
     */
    public PetFeignRespDTO requireOwnedIfPresent(Long pid) {
        if (pid == null) {
            return null;
        }
        // 先确认本服务拿到了登录身份，再去问 pet 服务：否则「未登录」会被下游拒绝、
        // 再被包装成"宠物服务暂不可用"，前端看到的是服务故障而不是"请重新登录"
        VipUserHelper.currentUid();
        for (PetFeignRespDTO pet : listMyPets()) {
            if (pid.equals(pet.getPid())) {
                return pet;
            }
        }
        log.warn("宠物归属校验未通过，pid={}", pid);
        throw new ClientException(VipErrorCodeEnum.VIP_3D_PET_NOT_OWNED);
    }

    /**
     * 当前登录用户的宠物列表
     */
    private List<PetFeignRespDTO> listMyPets() {
        Result<List<PetFeignRespDTO>> result;
        try {
            result = petServiceFeign.listMyPets();
        } catch (Exception e) {
            log.error("调用宠物服务查询宠物列表失败", e);
            throw new ServiceException(VipErrorCodeEnum.VIP_3D_PET_SERVICE_ERROR);
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            log.error("宠物服务返回异常结果：{}", result);
            throw new ServiceException(VipErrorCodeEnum.VIP_3D_PET_SERVICE_ERROR);
        }
        return result.getData();
    }
}