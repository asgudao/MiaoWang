package com.mapleleaf.petapp.module.pet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_breed")
public class Breed {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 物种: 1=猫 2=狗 */
    private Integer species;

    /** 护理周期配置 JSON: {"vaccine":30,"deworm":90,"nail":14,"bath":30,"checkup":180} */
    private String careInfo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
