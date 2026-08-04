package com.mapleleaf.petapp.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.common.JwtUtil;
import com.mapleleaf.petapp.common.exception.BusinessException;
import com.mapleleaf.petapp.module.user.dto.LoginResponse;
import com.mapleleaf.petapp.module.user.entity.User;
import com.mapleleaf.petapp.module.user.mapper.UserMapper;
import com.mapleleaf.petapp.module.user.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtil jwtUtil;

    public UserServiceImpl(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public User getByPhone(String phone) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
    }

    @Override
        @Override
    public LoginResponse login(String phone) {
        User user = getByPhone(phone);
        if (user == null) {
            throw new BusinessException(404, "用户不存在，请先注册");
        }
        String token = jwtUtil.generate(user.getId(), phone);
        return new LoginResponse(token, user.getId(), user.getNickname());
    }

    @Override
        @Override
    public User register(String phone, String nickname) {
        User exist = getByPhone(phone);
        if (exist != null) {
            throw new BusinessException(400, "该手机号已注册，请直接登录");
        }
        User user = new User();
        user.setPhone(phone);
        user.setNickname(nickname != null ? nickname : "萌宠主人");
        user.setMemberType(0);
        save(user);
        return user;
    }
}
