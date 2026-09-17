package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dao.entity.VipAdminOpLogDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPromoCodeDO;
import com.MapleLeaf.MiaoWang.dto.req.Vip3dCompleteReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipAdminChangePasswordReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipAdminCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipAdminLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipPromoCodeSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.Vip3dOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipAdminLoginRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipAdminRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;

import java.util.List;

/**
 * 后台管理（管理员体系 + 运营操作）
 *
 * <p>权限两级：{@code SUPER} 能管管理员账号与审计日志，{@code OPERATOR} 只能做业务操作。
 * 所有写操作都会落一条操作日志（见 {@code VipAdminAuthSupport#log}）。
 */
public interface VipAdminService {

    /**
     * 登录：返回后台 token（走请求头 {@code X-Admin-Token}）
     */
    VipAdminLoginRespDTO login(VipAdminLoginReqDTO requestParam);

    /**
     * 退出登录：立即吊销 token
     */
    void logout(String token);

    /**
     * 当前登录的管理员
     */
    VipAdminRespDTO currentAdmin();

    /**
     * 修改自己的密码；成功后<b>强制重新登录</b>（旧 token 立即失效）
     */
    void changePassword(VipAdminChangePasswordReqDTO requestParam, String token);

    /**
     * 3D 定制单列表（运营视角，可按状态过滤）
     */
    List<Vip3dOrderRespDTO> list3dOrders(Integer status);

    /**
     * 流转 3D 定制单状态（待处理 → 制作中 / 取消）
     */
    Vip3dOrderRespDTO update3dOrderStatus(String orderNo, Integer status);

    /**
     * 回填模型地址，定制单完成
     */
    Vip3dOrderRespDTO complete3dOrder(Vip3dCompleteReqDTO requestParam);

    /**
     * 会员订单查询（可按支付状态、订单号过滤）
     */
    List<VipOrderRespDTO> listVipOrders(Integer payStatus, String orderNo);

    /**
     * 运营代退（不受用户 7 天自助窗口限制）
     */
    VipOrderRespDTO refundVipOrder(String orderNo, String reason);

    /**
     * 促销码列表
     */
    List<VipPromoCodeDO> listPromoCodes();

    /**
     * 新增/编辑促销码
     */
    VipPromoCodeDO savePromoCode(VipPromoCodeSaveReqDTO requestParam);

    /**
     * 促销码上下架
     */
    void updatePromoCodeStatus(Long id, Integer status);

    /**
     * 管理员列表（仅超管）
     */
    List<VipAdminRespDTO> listAdmins();

    /**
     * 新增管理员（仅超管）
     */
    VipAdminRespDTO createAdmin(VipAdminCreateReqDTO requestParam);

    /**
     * 启用/禁用管理员（仅超管，且不能禁用自己）
     */
    void updateAdminStatus(Long adminId, Integer status);

    /**
     * 操作日志（仅超管）
     */
    List<VipAdminOpLogDO> listOpLogs(Integer limit);
}