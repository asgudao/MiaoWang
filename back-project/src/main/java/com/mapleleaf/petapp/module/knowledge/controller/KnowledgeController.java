package com.mapleleaf.petapp.module.knowledge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mapleleaf.petapp.common.PageResult;
import com.mapleleaf.petapp.common.Result;
import com.mapleleaf.petapp.module.knowledge.entity.Knowledge;
import com.mapleleaf.petapp.module.knowledge.entity.KnowledgeCategory;
import com.mapleleaf.petapp.module.knowledge.service.KnowledgeCategoryService;
import com.mapleleaf.petapp.module.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {
    private final KnowledgeService knowledgeService;
    private final KnowledgeCategoryService categoryService;

    @GetMapping
    public Result<PageResult<Knowledge>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long categoryId) {
        LambdaQueryWrapper<Knowledge> w = new LambdaQueryWrapper<>();
        if (categoryId != null) w.eq(Knowledge::getCategoryId, categoryId);
        w.orderByDesc(Knowledge::getCreatedAt);
        Page<Knowledge> result = knowledgeService.page(new Page<>(page, size), w);
        return Result.ok(PageResult.of(result.getTotal(), result.getPages(), result.getCurrent(), result.getRecords()));
    }

    @GetMapping("/search")
    public Result<PageResult<Knowledge>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        LambdaQueryWrapper<Knowledge> w = new LambdaQueryWrapper<>();
        w.like(Knowledge::getTitle, keyword).orderByDesc(Knowledge::getViewCount);
        Page<Knowledge> result = knowledgeService.page(new Page<>(page, size), w);
        return Result.ok(PageResult.of(result.getTotal(), result.getPages(), result.getCurrent(), result.getRecords()));
    }

    @GetMapping("/{id}")
    public Result<Knowledge> getDetail(@PathVariable Long id) {
        Knowledge knowledge = knowledgeService.getById(id);
        if (knowledge != null) {
            knowledge.setViewCount(knowledge.getViewCount() == null ? 1 : knowledge.getViewCount() + 1);
            knowledgeService.updateById(knowledge);
        }
        return Result.ok(knowledge);
    }

    @GetMapping("/categories")
    public Result<List<KnowledgeCategory>> listCategories() {
        return Result.ok(categoryService.list(new LambdaQueryWrapper<KnowledgeCategory>().orderByAsc(KnowledgeCategory::getSortOrder)));
    }
}
