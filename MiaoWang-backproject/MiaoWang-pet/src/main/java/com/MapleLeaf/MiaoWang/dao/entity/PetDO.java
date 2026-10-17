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
     * 生存状态（1=在世，2=已离世；库中 NOT NULL DEFAULT 1）
     *
     * <p>刻意与"删除"分开：{@link #deletionTime} / delFlag 表示用户<b>注销</b>这只宠物
     * （注销后不再出现在列表里），而离世后主人<b>仍然要能看到它</b> ⇒ 不能复用删除标记，
     * 必须单独一列。
     *
     * <p>默认在世：存量行加列后一律是 1，所以现有接口的行为一个字都没变（只是多回一个字段）。
     * ⚠️ 口径是 1/2 而不是 0/1（与同表 gender / sterilizedState 那两个两态字段不是一套口径）。
     *
     * <p>当前<b>没有</b>任何接口能改它 —— 离世由后续的"申报"流程改写，本模块暂不开放写入。
     */
    private Integer lifeStatus;

    /**
     * 注销时间戳
     */

    private Long deletionTime;


}
