package com.MapleLeaf.MiaoWang.dao.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("miaowang_pet")
@EqualsAndHashCode(callSuper = false)
public class PetDO {
    /**
     * pid 宠物id
     */
    private Long pid;

    /**
     * uid 用户id
     */
    private Long uid;

    /**
     * 用户名
     */
    private String username;

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

    /**
     * 注销时间戳
     */

    private Long deletionTime;


}
