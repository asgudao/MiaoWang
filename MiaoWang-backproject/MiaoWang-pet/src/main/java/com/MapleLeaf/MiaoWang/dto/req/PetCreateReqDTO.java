package com.MapleLeaf.MiaoWang.dto.req;


import lombok.Data;

@Data
public class PetCreateReqDTO {
    /**
     * 宠物名
     */
    private String p_name;

    /**
     * 对主人的称呼
     */
    private String owner_call_name;

    /**
     * 年龄
     */
    private int age;

}
