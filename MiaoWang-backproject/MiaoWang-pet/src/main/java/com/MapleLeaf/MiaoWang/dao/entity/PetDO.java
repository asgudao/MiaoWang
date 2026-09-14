package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("miaowang_pet")
@EqualsAndHashCode(callSuper = false)
public class PetDO extends BaseDO {
    /**
     * pid 宠物id
     */
    @TableId(type = IdType.AUTO)
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
    private String pName;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 年龄
     */
    private int age;

    /**
     * 注销时间戳
     */

    private Long deletionTime;


}
