package com.MapleLeaf.MiaoWang.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PetUpdateReqDTO {
    /**
     * 宠物名
     */
    @JsonProperty("pName")
    private String pName;

    /**
     * 大类（不传表示不改动；传了必须合法）
     */
    private String species;

    /**
     * 具体品种（不传表示不改动）
     */
    private String specificSpecies;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 出生日期（不传表示不改动，只传日期，格式 2020-05-01）
     */
    private LocalDate birth;

    /**
     * 性别（不传表示不改动；0=雌性，1=雄性）
     */
    private Integer gender;

    /**
     * 绝育状态（不传表示不改动；0=未绝育，1=已绝育）
     */
    private Integer sterilizedState;

}