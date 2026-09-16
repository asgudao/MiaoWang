package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.resp.VipPlanRespDTO;

import java.util.List;

/**
 * 会员档位服务
 */
public interface VipPlanService {

    /**
     * 查询可购买的档位列表（会员页展示，免费档不返回）
     */
    List<VipPlanRespDTO> listPurchasablePlans();
}