package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
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
import com.MapleLeaf.MiaoWang.service.VipAdminService;
import com.MapleLeaf.MiaoWang.support.VipAdminContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台管理接口（前缀 {@code /vip/admin}）
 *
 * <h3>鉴权方式（与 C 端完全不同，别混）</h3>
 * <ul>
 *   <li>网关：{@code /vip/admin/**} 在<b>白名单</b>里（否则网关会要求 C 端登录态），
 *       所以"能不能访问"由本服务的拦截器判定</li>
 *   <li>请求头：{@code X-Admin-Token}（登录接口换来的），不是 {@code Authorization}</li>
 *   <li>除了 {@code /login}，其余接口全部经过 {@code VipAdminAuthInterceptor}</li>
 *   <li>管理员账号与 C 端账号是两套体系（独立表、独立 token）</li>
 * </ul>
 *
 * <h3>权限</h3>
 * {@code OPERATOR} 可做业务操作；{@code /admin/**}（管理员账号）与 {@code /op-log} 仅 {@code SUPER}。
 */
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/admin")
@RequiredArgsConstructor
public class VipAdminController {

    private final VipAdminService vipAdminService;

    /**
     * 登录（免鉴权，唯一不需要 token 的接口）
     */
    @PostMapping("/login")
    public Result<VipAdminLoginRespDTO> login(@RequestBody VipAdminLoginReqDTO requestParam) {
        return Results.success(vipAdminService.login(requestParam));
    }

    /**
     * 退出登录（token 立即失效）
     */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(name = VipAdminContext.ADMIN_TOKEN_HEADER, required = false) String token) {
        vipAdminService.logout(token);
        return Results.success();
    }

    /**
     * 当前登录的管理员（前端刷新页面后恢复菜单权限用）
     */
    @GetMapping("/me")
    public Result<VipAdminRespDTO> me() {
        return Results.success(vipAdminService.currentAdmin());
    }

    /**
     * 修改自己的密码；成功后旧 token 失效，需要重新登录
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody VipAdminChangePasswordReqDTO requestParam,
                                       @RequestHeader(name = VipAdminContext.ADMIN_TOKEN_HEADER, required = false) String token) {
        vipAdminService.changePassword(requestParam, token);
        return Results.success();
    }

    /**
     * 3D 定制单列表（status 空=全部；0 待处理最靠前）
     */
    @GetMapping("/3d/order")
    public Result<List<Vip3dOrderRespDTO>> list3dOrders(@RequestParam(required = false) Integer status) {
        return Results.success(vipAdminService.list3dOrders(status));
    }

    /**
     * 流转定制单状态：1=制作中，3=取消（不能直接改成 2 已完成）
     */
    @PostMapping("/3d/order/status")
    public Result<Vip3dOrderRespDTO> update3dOrderStatus(@RequestParam String orderNo,
                                                         @RequestParam Integer status) {
        return Results.success(vipAdminService.update3dOrderStatus(orderNo, status));
    }

    /**
     * 回填模型地址，定制单完成
     */
    @PostMapping("/3d/order/complete")
    public Result<Vip3dOrderRespDTO> complete3dOrder(@RequestBody Vip3dCompleteReqDTO requestParam) {
        return Results.success(vipAdminService.complete3dOrder(requestParam));
    }

    /**
     * 会员订单查询（payStatus 空=全部；orderNo 支持精确查单）
     */
    @GetMapping("/order")
    public Result<List<VipOrderRespDTO>> listVipOrders(@RequestParam(required = false) Integer payStatus,
                                                       @RequestParam(required = false) String orderNo) {
        return Results.success(vipAdminService.listVipOrders(payStatus, orderNo));
    }

    /**
     * 运营代退（不受用户 7 天自助窗口限制）
     */
    @PostMapping("/order/refund")
    public Result<VipOrderRespDTO> refundVipOrder(@RequestParam String orderNo,
                                                  @RequestParam(required = false) String reason) {
        return Results.success(vipAdminService.refundVipOrder(orderNo, reason));
    }

    /**
     * 促销码列表
     */
    @GetMapping("/promo")
    public Result<List<VipPromoCodeDO>> listPromoCodes() {
        return Results.success(vipAdminService.listPromoCodes());
    }

    /**
     * 新增/编辑促销码（id 为空=新增）
     */
    @PostMapping("/promo")
    public Result<VipPromoCodeDO> savePromoCode(@RequestBody VipPromoCodeSaveReqDTO requestParam) {
        return Results.success(vipAdminService.savePromoCode(requestParam));
    }

    /**
     * 促销码上下架：0=停用 1=启用
     */
    @PostMapping("/promo/{id}/status")
    public Result<Void> updatePromoCodeStatus(@PathVariable Long id, @RequestParam Integer status) {
        vipAdminService.updatePromoCodeStatus(id, status);
        return Results.success();
    }

    /**
     * 管理员列表（仅超管）
     */
    @GetMapping("/admin")
    public Result<List<VipAdminRespDTO>> listAdmins() {
        return Results.success(vipAdminService.listAdmins());
    }

    /**
     * 新增管理员（仅超管）
     */
    @PostMapping("/admin")
    public Result<VipAdminRespDTO> createAdmin(@RequestBody VipAdminCreateReqDTO requestParam) {
        return Results.success(vipAdminService.createAdmin(requestParam));
    }

    /**
     * 启用/禁用管理员（仅超管，且不能禁用自己）
     */
    @PostMapping("/admin/{id}/status")
    public Result<Void> updateAdminStatus(@PathVariable Long id, @RequestParam Integer status) {
        vipAdminService.updateAdminStatus(id, status);
        return Results.success();
    }

    /**
     * 操作日志（仅超管）
     */
    @GetMapping("/op-log")
    public Result<List<VipAdminOpLogDO>> listOpLogs(@RequestParam(required = false) Integer limit) {
        return Results.success(vipAdminService.listOpLogs(limit));
    }
}