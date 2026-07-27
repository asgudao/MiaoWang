package com.mapleleaf.petapp.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String phone;
    private String nickname;
    private String avatar;

    /** 会员类型: 0=普通 1=月卡 2=季卡 3=年卡 */
    private Integer memberType;

    /** 会员到期时间 */
    private LocalDateTime memberExpire;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
