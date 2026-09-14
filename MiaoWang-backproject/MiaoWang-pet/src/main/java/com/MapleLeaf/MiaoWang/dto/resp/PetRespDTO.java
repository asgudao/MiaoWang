package com.MapleLeaf.MiaoWang.dto.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;


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
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 年龄
     */
    private int age;
}
