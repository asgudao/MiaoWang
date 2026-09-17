package com.MapleLeaf.MiaoWang.dto.req;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PetCreateReqDTO {
    /**
     * 宠物名
     */
    @JsonProperty("pName")
    private String pName;

    /**
     * 大类（必填，仅允许 PetSpeciesEnum 中的取值，前端下拉选项）
     */
    private String species;

    /**
     * 具体品种（可不填，用户自行填写）
     */
    private String specificSpecies;


    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 出生日期（可为空，只传日期，格式 2020-05-01）
     */
    private LocalDate birth;

    /**
     * 性别（可不填；0=雌性，1=雄性）
     */
    private Integer gender;

    /**
     * 绝育状态（可不填；0=未绝育，1=已绝育）
     */
    private Integer sterilizedState;

}
