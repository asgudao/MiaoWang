package com.mapleleaf.petapp.module.reminder.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.module.reminder.entity.ReminderPlan;
import com.mapleleaf.petapp.module.reminder.mapper.ReminderPlanMapper;
import com.mapleleaf.petapp.module.reminder.service.ReminderPlanService;
import org.springframework.stereotype.Service;

@Service
public class ReminderPlanServiceImpl extends ServiceImpl<ReminderPlanMapper, ReminderPlan> implements ReminderPlanService {
}
