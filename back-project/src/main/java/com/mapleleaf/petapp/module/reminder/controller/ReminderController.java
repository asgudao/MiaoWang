package com.mapleleaf.petapp.module.reminder.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mapleleaf.petapp.common.Result;
import com.mapleleaf.petapp.module.reminder.entity.ReminderPlan;
import com.mapleleaf.petapp.module.reminder.service.ReminderPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {
    private final ReminderPlanService reminderService;

    @GetMapping
    public Result<List<ReminderPlan>> list(@RequestParam Long petId) {
        return Result.ok(reminderService.list(
                new LambdaQueryWrapper<ReminderPlan>().eq(ReminderPlan::getPetId, petId).orderByAsc(ReminderPlan::getNextDate)));
    }

    @PostMapping
    public Result<ReminderPlan> create(@RequestBody ReminderPlan plan) {
        reminderService.save(plan);
        return Result.ok(plan);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ReminderPlan plan) {
        plan.setId(id);
        reminderService.updateById(plan);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reminderService.removeById(id);
        return Result.ok();
    }
}
