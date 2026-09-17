package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 过敏史
 */
@Data
@TableName("miaowang_pet_allergy_record")
@EqualsAndHashCode(callSuper = false)
public class AllergyRecordDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 宠物ID
     */
    private Long pid;

    /**
     * 归属用户ID
     */
    private Long uid;

    /**
     * 用户名（冗余，便于排查）
     */
    private String username;

    /**
     * 过敏原，如：鸡肉、某品牌猫粮、青霉素
     */
    private String allergen;

    /**
     * 严重程度：1=轻度 2=中度 3=重度
     */
    private Integer severity;

    /**
     * 过敏反应表现，如：呕吐、皮肤红疹
     */
    private String reaction;

    /**
     * 应对方式/备注
     */
    private String note;
}