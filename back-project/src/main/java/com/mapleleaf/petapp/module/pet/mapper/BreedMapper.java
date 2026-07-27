package com.mapleleaf.petapp.module.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mapleleaf.petapp.module.pet.entity.Breed;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BreedMapper extends BaseMapper<Breed> {
}
