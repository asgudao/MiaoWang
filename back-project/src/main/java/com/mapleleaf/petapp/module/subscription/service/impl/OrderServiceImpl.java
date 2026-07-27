package com.mapleleaf.petapp.module.subscription.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mapleleaf.petapp.module.subscription.entity.Order;
import com.mapleleaf.petapp.module.subscription.mapper.OrderMapper;
import com.mapleleaf.petapp.module.subscription.service.OrderService;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
}
