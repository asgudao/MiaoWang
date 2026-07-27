package com.mapleleaf.petapp.module.pet.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_pet")
public class Pet {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String name;
    private Long breedId;

    /** 物种: 1=猫 2=狗 */
    private Integer species;

    /** 性别: 1=公 2=母 */
    private Integer gender;

    /** 月龄 */
    private Integer age;

    /** 体重(kg) */
    private BigDecimal weight;
    private String avatar;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
