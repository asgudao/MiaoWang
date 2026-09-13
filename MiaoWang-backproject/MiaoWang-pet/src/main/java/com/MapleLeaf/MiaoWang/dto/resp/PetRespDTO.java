package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;


@Data
public class PetRespDTO {
    /**
     * 宠物名
     */
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
