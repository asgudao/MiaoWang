package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

@Data
public class PetCreateReqDTO {
    /**
     * 宠物名
     */
    private String pName;

    /**
     * 用户名
     */
    private String username;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 年龄
     */
    private int age;

}
