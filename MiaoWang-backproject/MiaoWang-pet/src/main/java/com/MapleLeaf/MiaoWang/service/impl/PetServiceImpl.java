package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.PetDO;
import com.MapleLeaf.MiaoWang.dao.mapper.PetMapper;
import com.MapleLeaf.MiaoWang.dto.req.PetCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.MapleLeaf.MiaoWang.service.PetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PetServiceImpl extends ServiceImpl<PetMapper,PetDO> implements PetService {

    @Override
    public List<PetRespDTO> getPetByUsername(String username) {
        LambdaQueryWrapper<PetDO> queryWrapper = Wrappers.lambdaQuery(PetDO.class)
                .eq(PetDO::getUsername, username);
        List<PetDO> petDOList = baseMapper.selectList(queryWrapper);
        if (petDOList == null || petDOList.isEmpty()) {
            throw new ClientException("宠物信息不存在");
        }
        List<PetRespDTO> result = petDOList.stream()
                .map(petDO -> BeanUtil.toBean(petDO, PetRespDTO.class))
                .collect(Collectors.toList());
        return result;
    }

    @Override
    public Void createPetByUsername(PetCreateReqDTO requestParam) {
        return null;
    }
}
