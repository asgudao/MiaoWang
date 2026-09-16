package com.MapleLeaf.MiaoWang.dao.entity;


import com.MapleLeaf.MiaoWang.common.dao.BaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 会员档位配置
 */
@Data
@TableName("miaowang_vip_plan")
@EqualsAndHashCode(callSuper = false)
public class VipPlanDO extends BaseDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 档位编码：FREE/PET_49/AI_149/AI_249/AI_349/ALL_400
     */
    private String planCode;

    /**
     * 档位名称
     */
    private String planName;

    /**
     * 月费（元）
     */
    private BigDecimal price;

    /**
     * 单次开通/续费的有效天数
     */
    private Integer durationDays;

    /**
     * 每种物种（猫、狗各自）可养数量上限
     */
    private Integer petLimitPerType;

    /**
     * RAG 每日次数，-1=不限次数
     */
    private Integer ragDailyLimit;

    /**
     * 宠物聊天每日轮次，-1=不限次数
     */
    private Integer chatDailyLimit;

    /**
     * 是否可购买：0=否 1=是
     */
    private Integer purchasable;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 排序值，越小越靠前
     */
    private Integer sort;
}