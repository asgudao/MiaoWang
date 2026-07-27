package com.mapleleaf.petapp.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mapleleaf.petapp.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
