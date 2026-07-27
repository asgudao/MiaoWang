package com.mapleleaf.petapp.module.reminder.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_reminder_plan")
public class ReminderPlan {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long petId;

    /** 提醒类型: vaccine/deworm/nail/bath/checkup */
    private String ruleType;

    private LocalDate nextDate;

    /** 间隔天数 */
    private Integer cycleDays;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
