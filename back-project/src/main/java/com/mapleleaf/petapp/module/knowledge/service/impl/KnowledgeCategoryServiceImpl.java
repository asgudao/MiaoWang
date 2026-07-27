package com.mapleleaf.petapp.module.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.module.knowledge.entity.KnowledgeCategory;
import com.mapleleaf.petapp.module.knowledge.mapper.KnowledgeCategoryMapper;
import com.mapleleaf.petapp.module.knowledge.service.KnowledgeCategoryService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeCategoryServiceImpl extends ServiceImpl<KnowledgeCategoryMapper, KnowledgeCategory> implements KnowledgeCategoryService {
}
