package com.mapleleaf.petapp.module.subscription.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_subscription_plan")
public class SubscriptionPlan {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 档位名称: 月卡/季卡/年卡 */
    private String name;

    /** 价格(分) */
    private Integer price;

    /** 有效天数 */
    private Integer durationDays;

    /** 宠物数量上限 */
    private Integer petLimit;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
