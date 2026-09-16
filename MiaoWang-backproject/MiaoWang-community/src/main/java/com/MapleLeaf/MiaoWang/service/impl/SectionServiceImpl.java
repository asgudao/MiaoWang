package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.MapleLeaf.MiaoWang.constant.CommunityConstant;
import com.MapleLeaf.MiaoWang.dao.entity.CommunitySectionDO;
import com.MapleLeaf.MiaoWang.dao.mapper.CommunitySectionMapper;
import com.MapleLeaf.MiaoWang.dto.resp.SectionRespDTO;
import com.MapleLeaf.MiaoWang.service.SectionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 社区版块查询实现（公开接口，不读 UserContext）
 */
@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {

    private final CommunitySectionMapper sectionMapper;

    @Override
    public List<SectionRespDTO> listEnabledSections() {
        List<CommunitySectionDO> sectionDOList = sectionMapper.selectList(Wrappers.lambdaQuery(CommunitySectionDO.class)
                .eq(CommunitySectionDO::getStatus, 1)
                .eq(CommunitySectionDO::getDelFlag, CommunityConstant.DEL_FLAG_NORMAL)
                .orderByAsc(CommunitySectionDO::getSort)
                .orderByAsc(CommunitySectionDO::getSectionId));
        return sectionDOList.stream()
                .map(sectionDO -> BeanUtil.toBean(sectionDO, SectionRespDTO.class))
                .collect(Collectors.toList());
    }
}