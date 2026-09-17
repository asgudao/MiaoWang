package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeCategoryRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
import com.MapleLeaf.MiaoWang.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库查询接口
 * 公开内容区：网关白名单放行，不校验 token，也不读 UserContext
 */
@RestController
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 知识分类列表
     *
     * @param species 物种：1=猫 2=狗，不传返回全部分类
     */
    @GetMapping("/mapleleaf/miaowang/v1/knowledge/category")
    public Result<List<KnowledgeCategoryRespDTO>> listCategories(@RequestParam(required = false) Integer species) {
        return Results.success(knowledgeService.listCategories(species));
    }

    /**
     * 已发布知识碎片列表
     *
     * @param species      物种：1=猫 2=狗
     * @param categoryCode 分类编码：diet/behavior/care/health
     */
    @GetMapping("/mapleleaf/miaowang/v1/knowledge/fragment")
    public Result<List<KnowledgeFragmentRespDTO>> listFragments(@RequestParam(required = false) Integer species,
                                                                @RequestParam(required = false) String categoryCode) {
        return Results.success(knowledgeService.listFragments(species, categoryCode));
    }

    /**
     * 已发布知识碎片分页列表（前端列表用）
     * <p>
     * 与 /fragment 的区别：这个是分页的，给前端翻页/上拉加载；
     * /fragment 保持"一次返回全量"的语义不变（供 RAG 索引重建等内部消费方使用）。
     *
     * @param species      物种：1=猫 2=狗
     * @param categoryCode 分类编码：diet/behavior/care/health
     * @param pageNum      页码，默认 1
     * @param pageSize     每页条数，默认 10，最大 50
     */
    @GetMapping("/mapleleaf/miaowang/v1/knowledge/fragment/page")
    public Result<PageRespDTO<KnowledgeFragmentRespDTO>> pageFragments(@RequestParam(required = false) Integer species,
                                                                       @RequestParam(required = false) String categoryCode,
                                                                       @RequestParam(required = false) Long pageNum,
                                                                       @RequestParam(required = false) Long pageSize) {
        return Results.success(knowledgeService.pageFragments(species, categoryCode, pageNum, pageSize));
    }

    /**
     * 知识碎片详情
     */
    @GetMapping("/mapleleaf/miaowang/v1/knowledge/fragment/{id}")
    public Result<KnowledgeFragmentRespDTO> getFragmentById(@PathVariable("id") Long id) {
        return Results.success(knowledgeService.getFragmentById(id));
    }

    /**
     * 关键词搜索已发布知识碎片（RAG 关键词召回路）
     *
     * @param keyword 关键词，空格分隔多词
     */
    @GetMapping("/mapleleaf/miaowang/v1/knowledge/fragment/search")
    public Result<List<KnowledgeFragmentRespDTO>> searchFragments(@RequestParam String keyword,
                                                                  @RequestParam(required = false) Integer species,
                                                                  @RequestParam(required = false) String categoryCode) {
        return Results.success(knowledgeService.searchFragments(keyword, species, categoryCode));
    }
}