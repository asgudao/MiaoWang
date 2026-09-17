package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.constant.RedisCacheConstant;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.KnowledgeConstant;
import com.MapleLeaf.MiaoWang.dao.entity.KnowledgeCategoryDO;
import com.MapleLeaf.MiaoWang.dao.entity.KnowledgeFragmentDO;
import com.MapleLeaf.MiaoWang.dao.mapper.KnowledgeCategoryMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.KnowledgeFragmentMapper;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeCategoryRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PageRespDTO;
import com.MapleLeaf.MiaoWang.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库查询实现：只查已发布（status=2）数据，公开接口不读 UserContext
 * <p>
 * 浏览量走 "Redis 计数 + 合并写回"，读路径不写数据库，见 {@code KnowledgeViewCountFlushTask}。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeFragmentMapper, KnowledgeFragmentDO> implements KnowledgeService {

    private final KnowledgeCategoryMapper categoryMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public List<KnowledgeCategoryRespDTO> listCategories(Integer species) {
        LambdaQueryWrapper<KnowledgeCategoryDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeCategoryDO.class)
                .eq(KnowledgeCategoryDO::getDelFlag, 0)
                .orderByAsc(KnowledgeCategoryDO::getSort, KnowledgeCategoryDO::getId);
        if (species != null) {
            queryWrapper.eq(KnowledgeCategoryDO::getSpecies, species);
        }
        return categoryMapper.selectList(queryWrapper).stream()
                .map(categoryDO -> BeanUtil.toBean(categoryDO, KnowledgeCategoryRespDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeFragmentRespDTO> listFragments(Integer species, String categoryCode) {
        // 保持"一次返回全量"的语义：RAG 索引重建（RagIndexService）依赖这个形状，不要改
        return baseMapper.selectList(publishedWrapper(species, categoryCode)
                        .orderByAsc(KnowledgeFragmentDO::getSpecies, KnowledgeFragmentDO::getCategoryCode, KnowledgeFragmentDO::getId))
                .stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    public PageRespDTO<KnowledgeFragmentRespDTO> pageFragments(Integer species, String categoryCode, Long pageNum, Long pageSize) {
        long page = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);
        IPage<KnowledgeFragmentDO> fragmentPage = baseMapper.selectPage(new Page<>(page, size),
                publishedWrapper(species, categoryCode)
                        .orderByAsc(KnowledgeFragmentDO::getSpecies, KnowledgeFragmentDO::getCategoryCode, KnowledgeFragmentDO::getId));
        List<KnowledgeFragmentRespDTO> records = fragmentPage.getRecords().stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
        return PageRespDTO.of(page, size, fragmentPage.getTotal(), records);
    }

    @Override
    public KnowledgeFragmentRespDTO getFragmentById(Long id) {
        KnowledgeFragmentDO fragmentDO = baseMapper.selectOne(Wrappers.lambdaQuery(KnowledgeFragmentDO.class)
                .eq(KnowledgeFragmentDO::getId, id)
                .eq(KnowledgeFragmentDO::getStatus, 2)
                .eq(KnowledgeFragmentDO::getDelFlag, 0));
        if (fragmentDO == null) {
            throw new ClientException("知识条目不存在");
        }
        KnowledgeFragmentRespDTO respDTO = toRespDTO(fragmentDO);
        // 浏览量：读路径只在 Redis 上加一，返回时把"已写回的值 + 还没写回的增量"一起给出去，
        // 这样用户看到的数字是实时的，而数据库不会被每次阅读打一次写
        long dbViewCount = fragmentDO.getViewCount() == null ? 0L : fragmentDO.getViewCount();
        respDTO.setViewCount(dbViewCount + incrementViewCount(id));
        return respDTO;
    }

    @Override
    public List<KnowledgeFragmentRespDTO> searchFragments(String keyword, Integer species, String categoryCode) {
        LambdaQueryWrapper<KnowledgeFragmentDO> queryWrapper = publishedWrapper(species, categoryCode)
                .orderByDesc(KnowledgeFragmentDO::getId)
                .last("LIMIT 30");
        if (StrUtil.isNotBlank(keyword)) {
            List<String> terms = Arrays.stream(keyword.trim().split("\\s+"))
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .limit(8)
                    .collect(Collectors.toList());
            if (!terms.isEmpty()) {
                queryWrapper.and(w -> {
                    for (int i = 0; i < terms.size(); i++) {
                        String term = terms.get(i);
                        if (i > 0) {
                            w.or();
                        }
                        // 每个词：title LIKE %词% OR content LIKE %词%
                        w.and(o -> o.like(KnowledgeFragmentDO::getTitle, term)
                                .or()
                                .like(KnowledgeFragmentDO::getContent, term));
                    }
                });
            }
        }
        return baseMapper.selectList(queryWrapper).stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
    }

    /**
     * 已发布（status=2）碎片的公共查询条件：列表、分页、搜索三处共用
     */
    private LambdaQueryWrapper<KnowledgeFragmentDO> publishedWrapper(Integer species, String categoryCode) {
        // status=2 已发布（表注释约定：0=草稿 1=待审 2=已发布 3=下架）
        LambdaQueryWrapper<KnowledgeFragmentDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeFragmentDO.class)
                .eq(KnowledgeFragmentDO::getStatus, 2)
                .eq(KnowledgeFragmentDO::getDelFlag, 0);
        if (species != null) {
            queryWrapper.eq(KnowledgeFragmentDO::getSpecies, species);
        }
        if (StrUtil.isNotBlank(categoryCode)) {
            queryWrapper.eq(KnowledgeFragmentDO::getCategoryCode, categoryCode);
        }
        return queryWrapper;
    }

    private KnowledgeFragmentRespDTO toRespDTO(KnowledgeFragmentDO fragmentDO) {
        // 列表里的 viewCount 取库里的值（最多滞后一个写回窗口），实时值只在详情接口里算
        return BeanUtil.toBean(fragmentDO, KnowledgeFragmentRespDTO.class);
    }

    /**
     * 详情阅读计数：Redis INCR（O(1)，不碰数据库），返回当前窗口内的累计增量
     */
    private long incrementViewCount(Long id) {
        String key = RedisCacheConstant.KNOWLEDGE_VIEW_KEY + id;
        Long delta = stringRedisTemplate.opsForValue().increment(key);
        if (delta != null && delta == 1L) {
            // 只在第一次阅读时设过期，之后不续期：即使写回任务一直失败，key 也会自己过期，不会堆成永久垃圾
            stringRedisTemplate.expire(key, KnowledgeConstant.VIEW_COUNT_KEY_TTL);
        }
        return delta == null ? 0L : delta;
    }

    private long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? KnowledgeConstant.DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return KnowledgeConstant.DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, KnowledgeConstant.MAX_PAGE_SIZE);
    }
}