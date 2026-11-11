package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import com.MapleLeaf.MiaoWang.dao.entity.VipPlanDO;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPlanMapper;
import com.MapleLeaf.MiaoWang.dto.resp.VipPlanRespDTO;
import com.MapleLeaf.MiaoWang.service.VipPlanService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VipPlanServiceImpl implements VipPlanService {

    private final VipPlanMapper vipPlanMapper;

    @Override
    public List<VipPlanRespDTO> listPurchasablePlans() {
        // 只返回「用户级」档位：宠物级档位（宠物纪念年包）是给已离世宠物的一次性商品，
        // 出现在通用会员页上等于向所有用户推销一个买不了的东西（产品硬规则②）。
        List<VipPlanDO> planDOList = vipPlanMapper.selectList(Wrappers.lambdaQuery(VipPlanDO.class)
                .eq(VipPlanDO::getPurchasable, 1)
                .eq(VipPlanDO::getPetScoped, 0)
                .eq(VipPlanDO::getStatus, 1)
                .eq(VipPlanDO::getDelFlag, 0)
                .orderByAsc(VipPlanDO::getSort));
        if (planDOList == null || planDOList.isEmpty()) {
            return List.of();
        }
        return planDOList.stream()
                .map(planDO -> BeanUtil.toBean(planDO, VipPlanRespDTO.class))
                .collect(Collectors.toList());
    }
}