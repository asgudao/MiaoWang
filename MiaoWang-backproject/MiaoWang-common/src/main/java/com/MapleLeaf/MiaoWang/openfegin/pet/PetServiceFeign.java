package com.MapleLeaf.MiaoWang.openfegin.pet;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.openfegin.config.FeignHeaderPropagationConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 宠物服务 Feign
 *
 * <p>归属校验思路：直接复用 pet 服务已有的「查我的宠物」接口 —— 它按登录态
 * （username + token → UserContext）返回当前用户的宠物列表。把登录头透传过去后，
 * 只要目标 pid 不在这个列表里，就说明这只宠物不属于当前用户。
 * 这样 pet 模块<b>不需要新增任何内部接口</b>，健康模块也不用去读别人的表。
 */
@FeignClient(value = "miaowang-pet", configuration = FeignHeaderPropagationConfig.class)
public interface PetServiceFeign {

    /**
     * 当前登录用户的宠物列表
     */
    @GetMapping("/mapleleaf/miaowang/v1/pet")
    Result<List<PetFeignRespDTO>> listMyPets();
}