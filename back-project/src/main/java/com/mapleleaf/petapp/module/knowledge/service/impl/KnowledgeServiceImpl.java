package com.mapleleaf.petapp.module.knowledge.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.module.knowledge.entity.Knowledge;
import com.mapleleaf.petapp.module.knowledge.mapper.KnowledgeMapper;
import com.mapleleaf.petapp.module.knowledge.service.KnowledgeService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {
}
