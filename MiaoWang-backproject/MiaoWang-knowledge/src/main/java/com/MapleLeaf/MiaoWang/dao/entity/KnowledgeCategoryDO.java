package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识分类字典
 */
@Data
@TableName("knowledge_category")
@EqualsAndHashCode(callSuper = false)
public class KnowledgeCategoryDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分类编码：diet=饮食安全 behavior=行为解读 care=日常养护 health=健康自查
     */
    private String code;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 适用物种：0=通用 1=猫 2=狗
     */
    private Integer species;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}