package com.MapleLeaf.MiaoWang.openfegin.pet;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.dto.resp.PetBriefRespDTO;
import com.MapleLeaf.MiaoWang.openfegin.config.FeignHeaderPropagationConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 调用宠物服务（社区个人主页要展示"他的宠物"）
 * <p>
 * 为什么不叫 PetServiceFeign：common 里已经有一份<b>同包同名</b>的 {@code PetServiceFeign}
 * （负责"查我的宠物"、给健康/会员模块做归属校验），两份内容不同却同名，谁生效取决于 classpath 顺序，
 * 是个随时会炸的隐患（决策记录 #54）。社区这份改名独立出来。
 * <p>
 * <b>contextId 必须留着</b>：改名之后，社区的 classpath 上就同时存在两个
 * {@code @FeignClient("miaowang-pet")} 的客户端了，不显式声明 contextId 会撞
 * FeignClientSpecification 的 bean 名（miaowang-pet.FeignClientSpecification），启动直接失败 ——
 * 现在没炸恰恰是因为同 FQN 只加载了一个类。
 * <p>
 * configuration = FeignHeaderPropagationConfig：把当前请求的 username/token 透传给宠物服务，
 * 那个类刻意没加 @Configuration，就是要求这样按客户端引用，避免变成全局拦截器。
 * <p>
 * 当前只有一个公开读接口，本身不依赖登录态；透传是为了以后扩展成"按当前用户过滤"时不用改。
 */
@FeignClient(value = "miaowang-pet", contextId = "petProfileFeign",
        configuration = FeignHeaderPropagationConfig.class)
public interface PetProfileFeign {

    /**
     * 按 uid 查看某个用户的宠物列表
     *
     * @param uid 用户id
     */
    @GetMapping("/mapleleaf/miaowang/v1/pet/user/{uid}")
    Result<List<PetBriefRespDTO>> getPetsByUid(@PathVariable("uid") Long uid);
}