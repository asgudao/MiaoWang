package com.mapleleaf.petapp.module.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mapleleaf.petapp.module.user.dto.LoginResponse;
import com.mapleleaf.petapp.module.user.entity.User;

public interface UserService extends IService<User> {
    User getByPhone(String phone);
    LoginResponse login(String phone);
    User register(String phone, String nickname);
}
