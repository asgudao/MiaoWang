package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

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
     * 大类（猫/狗/蛇等，库中非空，取值受 PetSpeciesEnum 约束）
     */
    private String species;

    /**
     * 具体品种（可为空，用户自行填写）
     */
    private String specificSpecies;

    /**
     * 对主人的称呼
     */
    private String ownerCallName;

    /**
     * 出生日期（库中 datetime，只取日期，存当天 00:00:00）
     */
    private LocalDateTime birth;

    /**
     * 性别（0=雌性，1=雄性）
     */
    private Integer gender;

    /**
     * 绝育状态（0=未绝育，1=已绝育）
     */
    private Integer sterilizedState;

    /**
     * 注销时间戳
     */

    private Long deletionTime;


}
