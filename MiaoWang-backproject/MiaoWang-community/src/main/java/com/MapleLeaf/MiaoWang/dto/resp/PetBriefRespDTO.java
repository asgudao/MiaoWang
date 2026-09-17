package com.MapleLeaf.MiaoWang.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

/**
 * 宠物信息的精简视图（社区侧）
 * <p>
 * 社区不依赖宠物服务的 DTO 类，只按 JSON 字段名映射自己需要的字段：
 * 跨服务共享 DTO 会把两个模块的发布绑在一起，这里用"字段对齐"换解耦。
 * 宠物服务返回的多余字段 Jackson 默认忽略。
 */
@Data
public class PetBriefRespDTO {

    /**
     * 宠物id
     */
    private Long pid;

    /**
     * 宠物名（宠物服务用 @JsonProperty("pName") 序列化）
     */
    @JsonProperty("pName")
    private String pName;

    /**
     * 大类（猫/狗/蛇等）
     */
    private String species;

    /**
     * 具体品种
     */
    private String specificSpecies;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 出生日期
     */
    private LocalDate birth;

    /**
     * 性别（0=雌性，1=雄性）
     */
    private Integer gender;

    /**
     * 绝育状态（0=未绝育，1=已绝育）
     */
    private Integer sterilizedState;
}