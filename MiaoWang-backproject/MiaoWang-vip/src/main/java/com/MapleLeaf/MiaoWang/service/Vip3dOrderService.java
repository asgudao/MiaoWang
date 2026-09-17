package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.Vip3dApplyReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.Vip3dCompleteReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.Vip3dOrderRespDTO;

import java.util.List;

/**
 * 3D 宠物模型定制服务
 * 流程：用户提交需求（待处理）→ 制作方接手（制作中）→ 回填模型地址（已完成）
 * 模型归属：订单绑定 uid，模型地址只有下单本人可取
 */
public interface Vip3dOrderService {

    /**
     * 提交定制申请
     */
    Vip3dOrderRespDTO apply(Vip3dApplyReqDTO requestParam);

    /**
     * 当前用户的定制订单列表
     */
    List<Vip3dOrderRespDTO> listMyOrders();

    /**
     * 查询当前用户的某条定制订单
     */
    Vip3dOrderRespDTO getMyOrder(String orderNo);

    /**
     * 取定制模型地址（仅下单本人可取，他人一律视为订单不存在）
     */
    String getModelUrl(String orderNo);

    /**
     * 用户取消定制（仅待处理/制作中可取消）
     */
    Vip3dOrderRespDTO cancel(String orderNo);

    /**
     * 制作方回填模型地址，订单置为已完成（内部/运营接口）
     */
    Vip3dOrderRespDTO complete(Vip3dCompleteReqDTO requestParam);

    /**
     * 运营：按状态查定制单（{@code null} = 全部），后台列表用
     */
    List<Vip3dOrderRespDTO> listByStatus(Integer status);

    /**
     * 运营：流转定制单状态（0 待处理 / 1 制作中 / 3 已取消）
     *
     * <p>刻意不允许改成「已完成」：完成必须带模型地址，走 {@link #complete}，
     * 否则会出现"已完成但取不到模型"的空单。
     */
    Vip3dOrderRespDTO updateStatus(String orderNo, Integer status);
}