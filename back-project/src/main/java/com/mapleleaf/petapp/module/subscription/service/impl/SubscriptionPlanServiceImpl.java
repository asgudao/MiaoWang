package com.mapleleaf.petapp.module.subscription.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.module.subscription.entity.SubscriptionPlan;
import com.mapleleaf.petapp.module.subscription.mapper.SubscriptionPlanMapper;
import com.mapleleaf.petapp.module.subscription.service.SubscriptionPlanService;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionPlanServiceImpl extends ServiceImpl<SubscriptionPlanMapper, SubscriptionPlan> implements SubscriptionPlanService {
}
