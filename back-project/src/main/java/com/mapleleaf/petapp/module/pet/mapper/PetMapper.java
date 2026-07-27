package com.mapleleaf.petapp.module.pet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mapleleaf.petapp.module.pet.entity.Pet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PetMapper extends BaseMapper<Pet> {
    @Select("SELECT COUNT(*) FROM t_pet WHERE user_id = #{userId} AND species = #{species} AND deleted = 0")
    long countByUserAndSpecies(Long userId, Integer species);
}
