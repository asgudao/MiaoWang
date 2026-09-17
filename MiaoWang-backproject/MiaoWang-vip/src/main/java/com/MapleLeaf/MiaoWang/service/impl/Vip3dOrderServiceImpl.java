package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.Vip3dOrderDO;
import com.MapleLeaf.MiaoWang.dao.mapper.Vip3dOrderMapper;
import com.MapleLeaf.MiaoWang.dto.req.Vip3dApplyReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.Vip3dCompleteReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.Vip3dOrderRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.Vip3dOrderService;
import com.MapleLeaf.MiaoWang.support.VipPetOwnershipChecker;
import com.MapleLeaf.MiaoWang.support.VipUserHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Vip3dOrderServiceImpl implements Vip3dOrderService {

    /**
     * 待处理：用户刚提交，等待制作方接手
     */
    private static final int STATUS_PENDING = 0;

    /**
     * 制作中
     */
    private static final int STATUS_MAKING = 1;

    /**
     * 已完成：模型地址已回填，用户可在首页展示
     */
    private static final int STATUS_DONE = 2;

    /**
     * 已取消
     */
    private static final int STATUS_CANCELED = 3;

    private final Vip3dOrderMapper vip3dOrderMapper;

    private final VipPetOwnershipChecker vipPetOwnershipChecker;

    @Override
    public Vip3dOrderRespDTO apply(Vip3dApplyReqDTO requestParam) {
        Long uid = VipUserHelper.currentUid();
        if (requestParam == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_PARAM_ERROR);
        }
        // 绑了宠物档案就必须是自己的宠物（否则能拿别人的 pid 下单），
        // 并顺手用档案里的名字覆盖前端传的宠物名，避免传错或伪造
        PetFeignRespDTO ownedPet = vipPetOwnershipChecker.requireOwnedIfPresent(requestParam.getPid());
        String petName = requestParam.getPetName();
        if (ownedPet != null && StrUtil.isNotBlank(ownedPet.getPName())) {
            petName = ownedPet.getPName();
        }
        if (StrUtil.isBlank(petName)) {
            throw new ClientException("请填写定制模型的宠物名");
        }
        if (StrUtil.isBlank(requestParam.getRequirement())) {
            throw new ClientException("请填写定制需求，例如毛色、体型、想突出的特征");
        }
        Vip3dOrderDO orderDO = new Vip3dOrderDO();
        orderDO.setOrderNo(generateOrderNo());
        orderDO.setUid(uid);
        orderDO.setUsername(VipUserHelper.currentUsername());
        orderDO.setPid(requestParam.getPid());
        orderDO.setPetName(petName.trim());
        orderDO.setRequirement(requestParam.getRequirement());
        orderDO.setReferenceImages(requestParam.getReferenceImages());
        orderDO.setStatus(STATUS_PENDING);
        vip3dOrderMapper.insert(orderDO);
        log.info("用户 {} 提交 3D 定制申请，单号 {}，宠物 {}", uid, orderDO.getOrderNo(), orderDO.getPetName());
        return toRespDTO(orderDO);
    }

    @Override
    public List<Vip3dOrderRespDTO> listMyOrders() {
        Long uid = VipUserHelper.currentUid();
        List<Vip3dOrderDO> orderDOList = vip3dOrderMapper.selectList(Wrappers.lambdaQuery(Vip3dOrderDO.class)
                .eq(Vip3dOrderDO::getUid, uid)
                .eq(Vip3dOrderDO::getDelFlag, 0)
                .orderByDesc(Vip3dOrderDO::getCreateTime));
        if (orderDOList == null || orderDOList.isEmpty()) {
            return List.of();
        }
        return orderDOList.stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Vip3dOrderRespDTO getMyOrder(String orderNo) {
        return toRespDTO(queryMyOrder(orderNo));
    }

    @Override
    public String getModelUrl(String orderNo) {
        Vip3dOrderDO orderDO = queryMyOrder(orderNo);
        if (orderDO.getStatus() == null || orderDO.getStatus() != STATUS_DONE || StrUtil.isBlank(orderDO.getModelUrl())) {
            throw new ClientException("定制模型还在制作中，请耐心等待");
        }
        return orderDO.getModelUrl();
    }

    @Override
    public Vip3dOrderRespDTO cancel(String orderNo) {
        Vip3dOrderDO orderDO = queryMyOrder(orderNo);
        if (orderDO.getStatus() == null
                || (orderDO.getStatus() != STATUS_PENDING && orderDO.getStatus() != STATUS_MAKING)) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_STATUS_ERROR);
        }
        orderDO.setStatus(STATUS_CANCELED);
        vip3dOrderMapper.updateById(orderDO);
        return toRespDTO(orderDO);
    }

    @Override
    public Vip3dOrderRespDTO complete(Vip3dCompleteReqDTO requestParam) {
        if (requestParam == null || StrUtil.isBlank(requestParam.getOrderNo())) {
            throw new ClientException("请指定要处理的定制单号");
        }
        if (StrUtil.isBlank(requestParam.getModelUrl())) {
            throw new ClientException("请填写定制模型的访问地址");
        }
        Vip3dOrderDO orderDO = vip3dOrderMapper.selectOne(Wrappers.lambdaQuery(Vip3dOrderDO.class)
                .eq(Vip3dOrderDO::getOrderNo, requestParam.getOrderNo())
                .eq(Vip3dOrderDO::getDelFlag, 0)
                .last("limit 1"));
        if (orderDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_NOT_EXIST);
        }
        if (orderDO.getStatus() != null
                && (orderDO.getStatus() == STATUS_DONE || orderDO.getStatus() == STATUS_CANCELED)) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_STATUS_ERROR);
        }
        orderDO.setStatus(STATUS_DONE);
        orderDO.setModelUrl(requestParam.getModelUrl());
        orderDO.setAdminRemark(requestParam.getAdminRemark());
        orderDO.setFinishTime(new Date());
        vip3dOrderMapper.updateById(orderDO);
        return toRespDTO(orderDO);
    }

    /**
     * 按「单号 + 归属人」查询：查不到就是查不到，
     * 不区分「订单不存在」和「不是你的订单」，避免泄露他人订单信息
     */
    private Vip3dOrderDO queryMyOrder(String orderNo) {
        if (StrUtil.isBlank(orderNo)) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_NOT_EXIST);
        }
        Long uid = VipUserHelper.currentUid();
        Vip3dOrderDO orderDO = vip3dOrderMapper.selectOne(Wrappers.lambdaQuery(Vip3dOrderDO.class)
                .eq(Vip3dOrderDO::getOrderNo, orderNo)
                .eq(Vip3dOrderDO::getUid, uid)
                .eq(Vip3dOrderDO::getDelFlag, 0)
                .last("limit 1"));
        if (orderDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_NOT_EXIST);
        }
        return orderDO;
    }

    @Override
    public List<Vip3dOrderRespDTO> listByStatus(Integer status) {
        List<Vip3dOrderDO> orderDOList = vip3dOrderMapper.selectList(Wrappers.lambdaQuery(Vip3dOrderDO.class)
                .eq(status != null, Vip3dOrderDO::getStatus, status)
                .eq(Vip3dOrderDO::getDelFlag, 0)
                // 待处理的排最前：后台一进来就先看到要接手的活
                .orderByAsc(Vip3dOrderDO::getStatus)
                .orderByDesc(Vip3dOrderDO::getCreateTime));
        if (orderDOList == null || orderDOList.isEmpty()) {
            return List.of();
        }
        return orderDOList.stream()
                .map(this::toRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Vip3dOrderRespDTO updateStatus(String orderNo, Integer status) {
        if (status == null || (status != STATUS_PENDING && status != STATUS_MAKING && status != STATUS_CANCELED)) {
            // 「已完成」不能靠改状态达成：必须回填模型地址走 complete()
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_STATUS_ERROR);
        }
        Vip3dOrderDO orderDO = vip3dOrderMapper.selectOne(Wrappers.lambdaQuery(Vip3dOrderDO.class)
                .eq(Vip3dOrderDO::getOrderNo, orderNo)
                .eq(Vip3dOrderDO::getDelFlag, 0)
                .last("limit 1"));
        if (orderDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_NOT_EXIST);
        }
        if (orderDO.getStatus() != null
                && (orderDO.getStatus() == STATUS_DONE || orderDO.getStatus() == STATUS_CANCELED)) {
            // 已完成/已取消是终态，不允许再改（避免"取消后又变制作中"这种脏数据）
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_STATUS_ERROR);
        }
        Vip3dOrderDO updateDO = new Vip3dOrderDO();
        updateDO.setId(orderDO.getId());
        updateDO.setStatus(status);
        if (vip3dOrderMapper.updateById(updateDO) < 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_3D_ORDER_STATUS_ERROR);
        }
        log.info("定制单 {} 状态由 {} 改为 {}", orderNo, statusDesc(orderDO.getStatus()), statusDesc(status));
        return toRespDTO(vip3dOrderMapper.selectById(orderDO.getId()));
    }

    private Vip3dOrderRespDTO toRespDTO(Vip3dOrderDO orderDO) {
        Vip3dOrderRespDTO respDTO = BeanUtil.toBean(orderDO, Vip3dOrderRespDTO.class);
        respDTO.setStatusDesc(statusDesc(orderDO.getStatus()));
        return respDTO;
    }

    private String statusDesc(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_PENDING -> "待处理";
            case STATUS_MAKING -> "制作中";
            case STATUS_DONE -> "已完成";
            case STATUS_CANCELED -> "已取消";
            default -> "未知";
        };
    }

    private String generateOrderNo() {
        return "D3" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(4);
    }
}