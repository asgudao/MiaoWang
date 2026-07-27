package com.mapleleaf.petapp.module.subscription.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mapleleaf.petapp.common.Result;
import com.mapleleaf.petapp.module.subscription.entity.Order;
import com.mapleleaf.petapp.module.subscription.entity.SubscriptionPlan;
import com.mapleleaf.petapp.module.subscription.service.OrderService;
import com.mapleleaf.petapp.module.subscription.service.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionPlanService planService;
    private final OrderService orderService;

    @GetMapping("/plans")
    public Result<List<SubscriptionPlan>> listPlans() {
        return Result.ok(planService.list(new LambdaQueryWrapper<SubscriptionPlan>().orderByAsc(SubscriptionPlan::getPrice)));
    }

    @PostMapping("/order")
    public Result<Order> createOrder(@RequestParam Long userId, @RequestParam Long planId) {
        SubscriptionPlan plan = planService.getById(planId);
        Order order = new Order();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        order.setUserId(userId);
        order.setPlanId(planId);
        order.setAmount(plan.getPrice());
        order.setStatus(0);
        order.setCreatedAt(LocalDateTime.now());
        orderService.save(order);
        return Result.ok(order);
    }

    @PostMapping("/callback")
    public Result<Void> payCallback(@RequestParam String orderNo) {
        Order order = orderService.getOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order != null && order.getStatus() == 0) {
            order.setStatus(1);
            order.setPaidAt(LocalDateTime.now());
            orderService.updateById(order);
        }
        return Result.ok();
    }
}
