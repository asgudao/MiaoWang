package com.MapleLeaf.MiaoWang.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class PetUpdateReqDTO {
    /**
     * 宠物名
     */
    @JsonProperty("pName")
    private String pName;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 年龄
     */
    private int age;

}