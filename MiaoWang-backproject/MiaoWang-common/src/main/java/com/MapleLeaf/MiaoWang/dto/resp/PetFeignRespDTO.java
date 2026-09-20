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
     * 具体品种（可为空，如「橘猫」）。ai-pet 拼宠物人设时会用上，让"我是一只橘猫"这种自我介绍成立
     */
    private String specificSpecies;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 出生日期，格式 {@code 2020-05-01}。
     *
     * <p>这里刻意用 String 而不是 LocalDate：对端 pet 服务回的是日期串，
     * 用 String 接可以完全绕开 JavaTime 在 Feign 解码链上的时间模块/格式配置问题
     * （日期格式只在"算年龄"一处需要，本模块自己解析，解析失败就当未知）。
     */
    private String birth;

    /**
     * 性别（0=雌性，1=雄性，可能为空）
     */
    private Integer gender;

    /**
     * 绝育状态（0=未绝育，1=已绝育，可能为空）
     */
    private Integer sterilizedState;
}