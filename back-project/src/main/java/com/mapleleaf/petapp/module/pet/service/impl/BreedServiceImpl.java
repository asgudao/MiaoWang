package com.mapleleaf.petapp.module.pet.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.module.pet.entity.Breed;
import com.mapleleaf.petapp.module.pet.mapper.BreedMapper;
import com.mapleleaf.petapp.module.pet.service.BreedService;
import org.springframework.stereotype.Service;

@Service
public class BreedServiceImpl extends ServiceImpl<BreedMapper, Breed> implements BreedService {
}
