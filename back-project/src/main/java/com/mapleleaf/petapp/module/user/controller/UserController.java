package com.mapleleaf.petapp.module.user.controller;

import com.mapleleaf.petapp.common.Result;
import com.mapleleaf.petapp.module.user.entity.User;
import com.mapleleaf.petapp.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取当前用户信息 */
    @GetMapping("/profile")
    public Result<User> getProfile() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userService.getById(userId);
        user.setPhone(null); // 不暴露手机号
        return Result.ok(user);
    }

    /** 修改个人信息 */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody User user) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        user.setId(userId);
        user.setPhone(null);      // 不允许修改手机号
        user.setMemberType(null); // 不允许修改会员类型
        userService.updateById(user);
        return Result.ok();
    }
}
