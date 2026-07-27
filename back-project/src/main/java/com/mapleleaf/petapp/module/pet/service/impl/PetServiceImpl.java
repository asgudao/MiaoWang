package com.mapleleaf.petapp.module.pet.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.common.exception.BusinessException;
import com.mapleleaf.petapp.module.pet.entity.Pet;
import com.mapleleaf.petapp.module.pet.mapper.PetMapper;
import com.mapleleaf.petapp.module.pet.service.PetService;
import com.mapleleaf.petapp.module.user.entity.User;
import com.mapleleaf.petapp.module.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class PetServiceImpl extends ServiceImpl<PetMapper, Pet> implements PetService {

    private final UserMapper userMapper;

    public PetServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean save(Pet pet) {
        checkPetLimit(pet.getUserId(), pet.getSpecies());
        return super.save(pet);
    }

    private void checkPetLimit(Long userId, Integer species) {
        User user = userMapper.selectById(userId);
        if (user == null) return;
        int limit = (user.getMemberType() != null && user.getMemberType() > 0) ? 99 : 1;
        long count = baseMapper.countByUserAndSpecies(userId, species);
        if (count >= limit) {
            String name = species == 1 ? "猫" : "狗";
            throw new BusinessException("普通用户最多养" + limit + "只" + name + "，升级会员可养更多哦~");
        }
    }
}
