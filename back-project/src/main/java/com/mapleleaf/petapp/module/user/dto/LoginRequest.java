package com.mapleleaf.petapp.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 验证码，MVP阶段可简化为固定码 */
    private String code;
}
