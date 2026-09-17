package com.MapleLeaf.MiaoWang.dto.resp;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 宠物信息（Feign 跨服务调用的镜像对象）
 *
 * <p>为什么不直接复用 MiaoWang-pet 里的 PetRespDTO：common 不能反向依赖业务模块，
 * 跨服务调用所需的 DTO 只能在 common 侧定义一份镜像，字段按需裁剪即可
 * （对端多出来的字段反序列化时会被自动忽略）。
 */
@Data
public class PetFeignRespDTO {

    /**
     * 宠物ID
     */
    private Long pid;

    /**
     * 宠物名
     */
    @JsonProperty("pName")
    private String pName;

    /**
     * 大类（猫/狗等）
     */
    private String species;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;
}