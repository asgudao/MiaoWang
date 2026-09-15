package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeCategoryRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;

import java.util.List;

/**
 * 知识库查询服务（公开只读，不依赖登录态）
 */
public interface KnowledgeService {

    /**
     * 分类列表，species 为空返回全部分类
     *
     * @param species 物种：1=猫 2=狗
     */
    List<KnowledgeCategoryRespDTO> listCategories(Integer species);

    /**
     * 已发布知识碎片列表，按 物种 + 分类 过滤
     *
     * @param species      物种：1=猫 2=狗，可为空
     * @param categoryCode 分类编码：diet/behavior/care/health，可为空
     */
    List<KnowledgeFragmentRespDTO> listFragments(Integer species, String categoryCode);

    /**
     * 知识碎片详情
     */
    KnowledgeFragmentRespDTO getFragmentById(Long id);

    /**
     * 关键词搜索已发布知识碎片（RAG 关键词召回路）
     * <p>
     * keyword 支持空格分隔多词，任意词命中 title/content 即返回；LIMIT 30
     *
     * @param keyword      关键词（空格分隔多词）
     * @param species      物种：1=猫 2=狗，可为空
     * @param categoryCode 分类编码，可为空
     */
    List<KnowledgeFragmentRespDTO> searchFragments(String keyword, Integer species, String categoryCode);
}