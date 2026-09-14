package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.KnowledgeCategoryDO;
import com.MapleLeaf.MiaoWang.dao.entity.KnowledgeFragmentDO;
import com.MapleLeaf.MiaoWang.dao.mapper.KnowledgeCategoryMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.KnowledgeFragmentMapper;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeCategoryRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.KnowledgeFragmentRespDTO;
import com.MapleLeaf.MiaoWang.service.KnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库查询实现：只查已发布（status=2）数据，公开接口不读 UserContext
 */
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeFragmentMapper, KnowledgeFragmentDO> implements KnowledgeService {

    private final KnowledgeCategoryMapper categoryMapper;

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
        // status=2 已发布（表注释约定：0=草稿 1=待审 2=已发布 3=下架）
        LambdaQueryWrapper<KnowledgeFragmentDO> queryWrapper = Wrappers.lambdaQuery(KnowledgeFragmentDO.class)
                .eq(KnowledgeFragmentDO::getStatus, 2)
                .eq(KnowledgeFragmentDO::getDelFlag, 0)
                .orderByAsc(KnowledgeFragmentDO::getSpecies, KnowledgeFragmentDO::getCategoryCode, KnowledgeFragmentDO::getId);
        if (species != null) {
            queryWrapper.eq(KnowledgeFragmentDO::getSpecies, species);
        }
        if (StrUtil.isNotBlank(categoryCode)) {
            queryWrapper.eq(KnowledgeFragmentDO::getCategoryCode, categoryCode);
        }
        return baseMapper.selectList(queryWrapper).stream()
                .map(fragmentDO -> BeanUtil.toBean(fragmentDO, KnowledgeFragmentRespDTO.class))
                .collect(Collectors.toList());
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
        return BeanUtil.toBean(fragmentDO, KnowledgeFragmentRespDTO.class);
    }
}