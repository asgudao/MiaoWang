package com.MapleLeaf.MiaoWang.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;


@Data
public class PetRespDTO {
    /**
     * 宠物id
     */
    private Long pid;

    /**
     * 宠物名
     */
    @JsonProperty("pName")
    private String pName;

    /**
     * 大类（猫/狗/蛇等）
     */
    private String species;

    /**
     * 具体品种（可为空）
     */
    private String specificSpecies;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 出生日期（只回日期，格式 2020-05-01）
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
