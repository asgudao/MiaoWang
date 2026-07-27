package com.mapleleaf.petapp.module.user.controller;

import com.mapleleaf.petapp.common.JwtUtil;
import com.mapleleaf.petapp.common.Result;
import com.mapleleaf.petapp.module.user.dto.LoginRequest;
import com.mapleleaf.petapp.module.user.dto.LoginResponse;
import com.mapleleaf.petapp.module.user.entity.User;
import com.mapleleaf.petapp.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /** 手机号登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        LoginResponse resp = userService.login(req.getPhone());
        return Result.ok(resp);
    }

    /** 手机号注册 */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody LoginRequest req) {
        User user = userService.register(req.getPhone(), null);
        String token = jwtUtil.generate(user.getId(), user.getPhone());
        return Result.ok(new LoginResponse(token, user.getId(), user.getNickname()));
    }
}
