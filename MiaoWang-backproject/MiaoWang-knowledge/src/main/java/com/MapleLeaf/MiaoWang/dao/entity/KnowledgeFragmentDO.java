package com.MapleLeaf.MiaoWang.dao.entity;

import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识碎片（一条记录 = 一个知识碎片，也是后续 RAG 的一个 chunk）
 */
@Data
@TableName("knowledge_fragment")
@EqualsAndHashCode(callSuper = false)
public class KnowledgeFragmentDO extends BaseDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属分类编码：diet=饮食安全 behavior=行为解读 care=日常养护 health=健康自查
     */
    private String categoryCode;

    /**
     * 物种：1=猫 2=狗
     */
    private Integer species;

    /**
     * 品种ID，NULL=全品种通用
     */
    private Long breedId;

    /**
     * 条目名，如：疫苗接种、摇尾巴、巧克力
     */
    private String title;

    /**
     * 主内容
     */
    private String content;

    /**
     * 备注/补充说明
     */
    private String remark;

    /**
     * 适用阶段：幼年/成年/老年，NULL=通用
     */
    private String stage;

    /**
     * 内容类型：care_schedule=护理日程(提醒模块消费) guide=科普 warning=安全警示 medical=医疗自查
     */
    private String contentType;

    /**
     * 标签，逗号分隔
     */
    private String tags;

    /**
     * 阅读量（缓存热点演示用）
     */
    private Long viewCount;

    /**
     * 来源：EXCEL=手册导入 LLM=生成 MANUAL=人工
     */
    private String source;

    /**
     * 导入批次号
     */
    private String batchNo;

    /**
     * 状态：0=草稿 1=待审 2=已发布 3=下架
     */
    private Integer status;
}