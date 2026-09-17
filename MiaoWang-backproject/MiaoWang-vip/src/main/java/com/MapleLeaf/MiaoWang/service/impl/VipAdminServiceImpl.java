package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.VipAdminDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipAdminOpLogDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPromoCodeDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPlanDO;
import com.MapleLeaf.MiaoWang.dao.mapper.VipAdminMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipAdminOpLogMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPlanMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPromoCodeMapper;
import com.MapleLeaf.MiaoWang.dto.req.Vip3dCompleteReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipAdminChangePasswordReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipAdminCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipAdminLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipPromoCodeSaveReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.Vip3dOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipAdminLoginRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipAdminRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipAdminRoleEnum;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPromoTypeEnum;
import com.MapleLeaf.MiaoWang.service.Vip3dOrderService;
import com.MapleLeaf.MiaoWang.service.VipAdminService;
import com.MapleLeaf.MiaoWang.service.VipOrderService;
import com.MapleLeaf.MiaoWang.support.VipAdminAuthSupport;
import com.MapleLeaf.MiaoWang.support.VipAdminContext;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 后台管理实现
 *
 * <p>本类只做三件事：<b>校验权限/参数</b> → <b>调用已有业务服务</b> → <b>写审计日志</b>。
 * 刻意不在这里复制业务逻辑（退款、发会员只有一处实现），后台只是"另一个入口"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipAdminServiceImpl implements VipAdminService {

    /**
     * 操作日志默认/最大条数
     */
    private static final int OP_LOG_DEFAULT_LIMIT = 50;

    private static final int OP_LOG_MAX_LIMIT = 200;

    /**
     * 后台密码最小长度
     */
    private static final int PASSWORD_MIN_LENGTH = 8;

    /**
     * 列表查询上限：后台不做分页（管理端数据量小），但也不能让一次查询把库拖死
     */
    private static final int LIST_LIMIT = 200;

    private final VipAdminMapper vipAdminMapper;

    private final VipAdminOpLogMapper vipAdminOpLogMapper;

    private final VipPromoCodeMapper vipPromoCodeMapper;

    private final VipPlanMapper vipPlanMapper;

    private final VipAdminAuthSupport vipAdminAuthSupport;

    private final Vip3dOrderService vip3dOrderService;

    private final VipOrderService vipOrderService;

    @Override
    public VipAdminLoginRespDTO login(VipAdminLoginReqDTO requestParam) {
        String ip = vipAdminAuthSupport.currentIp();
        VipAdminDO adminDO = vipAdminAuthSupport.authenticate(
                requestParam == null ? null : requestParam.getUsername(),
                requestParam == null ? null : requestParam.getPassword(),
                ip);
        String token = vipAdminAuthSupport.issueToken(adminDO.getId());
        // 登录本身也要留痕，但登录接口不走拦截器，所以上下文要自己放/自己清
        VipAdminContext.set(adminDO);
        try {
            vipAdminAuthSupport.log("ADMIN_LOGIN", "ADMIN", String.valueOf(adminDO.getId()), "登录成功，ip=" + ip);
        } finally {
            VipAdminContext.clear();
        }
        log.info("后台登录成功：{}（{}），ip={}", adminDO.getUsername(), adminDO.getRole(), ip);
        VipAdminLoginRespDTO respDTO = new VipAdminLoginRespDTO();
        respDTO.setToken(token);
        respDTO.setAdminId(adminDO.getId());
        respDTO.setUsername(adminDO.getUsername());
        respDTO.setRealName(adminDO.getRealName());
        respDTO.setRole(adminDO.getRole());
        respDTO.setRoleDesc(VipAdminRoleEnum.descOf(adminDO.getRole()));
        respDTO.setTokenTtlMinutes(VipAdminAuthSupport.TOKEN_TTL_MINUTES);
        return respDTO;
    }

    @Override
    public void logout(String token) {
        vipAdminAuthSupport.log("ADMIN_LOGOUT", "ADMIN", VipAdminContext.currentAdminName(), "退出登录");
        vipAdminAuthSupport.revokeToken(token);
    }

    @Override
    public VipAdminRespDTO currentAdmin() {
        return toAdminRespDTO(requireCurrentAdmin());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(VipAdminChangePasswordReqDTO requestParam, String token) {
        VipAdminDO current = requireCurrentAdmin();
        String oldPassword = requestParam == null ? null : requestParam.getOldPassword();
        String newPassword = requestParam == null ? null : requestParam.getNewPassword();
        if (StrUtil.length(newPassword) < PASSWORD_MIN_LENGTH) {
            throw new ClientException("新密码至少 " + PASSWORD_MIN_LENGTH + " 位");
        }
        VipAdminDO adminDO = vipAdminMapper.selectById(current.getId());
        if (adminDO == null || StrUtil.isBlank(adminDO.getPasswordHash())
                || !BCrypt.checkpw(StrUtil.blankToDefault(oldPassword, ""), adminDO.getPasswordHash())) {
            throw new ClientException("原密码不正确");
        }
        VipAdminDO updateDO = new VipAdminDO();
        updateDO.setId(adminDO.getId());
        updateDO.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        if (vipAdminMapper.updateById(updateDO) < 1) {
            throw new ClientException("密码修改失败，请稍后重试");
        }
        vipAdminAuthSupport.log("ADMIN_PASSWORD_CHANGE", "ADMIN", adminDO.getUsername(), "修改了自己的密码");
        // 改完密码立即失效旧 token：避免"密码泄露了但旧会话还活着"
        vipAdminAuthSupport.revokeToken(token);
        log.info("管理员 {} 修改密码成功，旧 token 已失效", adminDO.getUsername());
    }

    @Override
    public List<Vip3dOrderRespDTO> list3dOrders(Integer status) {
        return vip3dOrderService.listByStatus(status);
    }

    @Override
    public Vip3dOrderRespDTO update3dOrderStatus(String orderNo, Integer status) {
        Vip3dOrderRespDTO respDTO = vip3dOrderService.updateStatus(orderNo, status);
        vipAdminAuthSupport.log("3D_STATUS_UPDATE", "3D_ORDER", orderNo, "status=" + status);
        return respDTO;
    }

    @Override
    public Vip3dOrderRespDTO complete3dOrder(Vip3dCompleteReqDTO requestParam) {
        Vip3dOrderRespDTO respDTO = vip3dOrderService.complete(requestParam);
        String orderNo = requestParam == null ? null : requestParam.getOrderNo();
        vipAdminAuthSupport.log("3D_COMPLETE", "3D_ORDER", orderNo, "回填模型地址："
                + (requestParam == null ? null : requestParam.getModelUrl())
                + "，备注：" + (requestParam == null ? null : requestParam.getAdminRemark()));
        return respDTO;
    }

    @Override
    public List<VipOrderRespDTO> listVipOrders(Integer payStatus, String orderNo) {
        return vipOrderService.listOrdersForAdmin(payStatus, orderNo, LIST_LIMIT);
    }

    @Override
    public VipOrderRespDTO refundVipOrder(String orderNo, String reason) {
        VipOrderRespDTO respDTO = vipOrderService.adminRefund(orderNo, reason);
        vipAdminAuthSupport.log("ORDER_REFUND", "VIP_ORDER", orderNo, "运营代退，原因：" + reason);
        return respDTO;
    }

    @Override
    public List<VipPromoCodeDO> listPromoCodes() {
        return vipPromoCodeMapper.selectList(Wrappers.lambdaQuery(VipPromoCodeDO.class)
                .eq(VipPromoCodeDO::getDelFlag, 0)
                .orderByDesc(VipPromoCodeDO::getCreateTime)
                .last("limit " + LIST_LIMIT));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipPromoCodeDO savePromoCode(VipPromoCodeSaveReqDTO requestParam) {
        if (requestParam == null || StrUtil.isBlank(requestParam.getCode())) {
            throw new ClientException("促销码不能为空");
        }
        // 码统一转大写：用户输入不会区分大小写，存库也统一，避免"WELCOME30 和 welcome30 是两个码"
        String code = requestParam.getCode().trim().toUpperCase(Locale.ROOT);
        VipPromoTypeEnum promoType = VipPromoTypeEnum.of(requestParam.getPromoType());
        if (promoType == null) {
            throw new ClientException("促销码类型只能是 PLAN（送会员时长）或 DISCOUNT（下单立减）");
        }
        if (promoType == VipPromoTypeEnum.PLAN) {
            if (StrUtil.isBlank(requestParam.getPlanCode())) {
                throw new ClientException("送会员时长的促销码必须指定档位");
            }
            if (requestParam.getDurationDays() == null || requestParam.getDurationDays() <= 0) {
                throw new ClientException("赠送天数必须大于 0");
            }
            // 发码时就校验档位存在：否则配置错误要等用户来核销才炸（运营还不一定看得到日志）
            if (vipPlanMapper.selectOne(Wrappers.lambdaQuery(VipPlanDO.class)
                    .eq(VipPlanDO::getPlanCode, requestParam.getPlanCode().trim())
                    .eq(VipPlanDO::getDelFlag, 0)
                    .last("limit 1")) == null) {
                throw new ClientException("档位不存在：" + requestParam.getPlanCode());
            }
        } else {
            if (requestParam.getDiscountAmount() == null
                    || requestParam.getDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ClientException("立减金额必须大于 0");
            }
        }
        VipPromoCodeDO saveDO = new VipPromoCodeDO();
        saveDO.setId(requestParam.getId());
        saveDO.setCode(code);
        saveDO.setName(StrUtil.blankToDefault(requestParam.getName(), code));
        saveDO.setPromoType(promoType.getCode());
        saveDO.setPlanCode(StrUtil.emptyToNull(requestParam.getPlanCode()));
        saveDO.setDurationDays(requestParam.getDurationDays());
        saveDO.setDiscountAmount(requestParam.getDiscountAmount());
        saveDO.setMinAmount(requestParam.getMinAmount() == null ? BigDecimal.ZERO : requestParam.getMinAmount());
        // 名额 -1 表示不限；默认给 1 而不是不限，避免"忘了填"变成无限发
        saveDO.setMaxUseCount(requestParam.getMaxUseCount() == null ? 1 : requestParam.getMaxUseCount());
        saveDO.setStartTime(parseTime(requestParam.getStartTime(), "生效时间"));
        saveDO.setEndTime(parseTime(requestParam.getEndTime(), "失效时间"));
        saveDO.setStatus(requestParam.getStatus() == null ? 1 : requestParam.getStatus());
        saveDO.setRemark(requestParam.getRemark());
        saveDO.setDelFlag(0);
        try {
            if (saveDO.getId() == null) {
                saveDO.setUsedCount(0);
                if (vipPromoCodeMapper.insert(saveDO) < 1) {
                    throw new ClientException("促销码保存失败");
                }
            } else {
                VipPromoCodeDO existDO = vipPromoCodeMapper.selectById(saveDO.getId());
                if (existDO == null) {
                    throw new ClientException(VipErrorCodeEnum.VIP_PROMO_NOT_EXIST);
                }
                // 编辑时不允许把已用名额改小/清零：那等于凭空多发名额，也会让计数对不上
                saveDO.setUsedCount(existDO.getUsedCount());
                if (vipPromoCodeMapper.updateById(saveDO) < 1) {
                    throw new ClientException("促销码保存失败");
                }
            }
        } catch (DuplicateKeyException e) {
            throw new ClientException("促销码已存在：" + code);
        }
        vipAdminAuthSupport.log("PROMO_SAVE", "PROMO_CODE", code,
                "类型=" + promoType.getCode() + "，名额=" + saveDO.getMaxUseCount()
                        + "，状态=" + saveDO.getStatus() + "，备注=" + saveDO.getRemark());
        return vipPromoCodeMapper.selectById(saveDO.getId());
    }

    @Override
    public void updatePromoCodeStatus(Long id, Integer status) {
        if (id == null || status == null || (status != 0 && status != 1)) {
            throw new ClientException("参数不正确");
        }
        VipPromoCodeDO existDO = vipPromoCodeMapper.selectById(id);
        if (existDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_NOT_EXIST);
        }
        VipPromoCodeDO updateDO = new VipPromoCodeDO();
        updateDO.setId(id);
        updateDO.setStatus(status);
        if (vipPromoCodeMapper.updateById(updateDO) < 1) {
            throw new ClientException("促销码状态更新失败");
        }
        vipAdminAuthSupport.log("PROMO_STATUS", "PROMO_CODE", existDO.getCode(), "状态=" + status);
    }

    @Override
    public List<VipAdminRespDTO> listAdmins() {
        vipAdminAuthSupport.requireSuper();
        List<VipAdminDO> adminDOList = vipAdminMapper.selectList(Wrappers.lambdaQuery(VipAdminDO.class)
                .eq(VipAdminDO::getDelFlag, 0)
                .orderByAsc(VipAdminDO::getId));
        return adminDOList.stream()
                .map(this::toAdminRespDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipAdminRespDTO createAdmin(VipAdminCreateReqDTO requestParam) {
        vipAdminAuthSupport.requireSuper();
        if (requestParam == null || StrUtil.isBlank(requestParam.getUsername())) {
            throw new ClientException("登录名不能为空");
        }
        if (StrUtil.length(requestParam.getPassword()) < PASSWORD_MIN_LENGTH) {
            throw new ClientException("初始密码至少 " + PASSWORD_MIN_LENGTH + " 位");
        }
        VipAdminRoleEnum role = VipAdminRoleEnum.of(requestParam.getRole());
        if (role == null) {
            throw new ClientException("角色只能是 SUPER 或 OPERATOR");
        }
        String username = requestParam.getUsername().trim();
        if (vipAdminAuthSupport.findByUsername(username) != null) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_USERNAME_EXIST);
        }
        VipAdminDO adminDO = new VipAdminDO();
        adminDO.setUsername(username);
        adminDO.setPasswordHash(BCrypt.hashpw(requestParam.getPassword(), BCrypt.gensalt()));
        adminDO.setRealName(requestParam.getRealName());
        adminDO.setRole(role.getCode());
        adminDO.setStatus(1);
        adminDO.setDelFlag(0);
        try {
            if (vipAdminMapper.insert(adminDO) < 1) {
                throw new ClientException("管理员创建失败");
            }
        } catch (DuplicateKeyException e) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_USERNAME_EXIST);
        }
        // 日志里只记登录名与角色，绝不记密码
        vipAdminAuthSupport.log("ADMIN_CREATE", "ADMIN", username, "角色=" + role.getCode());
        log.info("新增后台管理员：{}（{}），操作人={}", username, role.getCode(), VipAdminContext.currentAdminName());
        return toAdminRespDTO(adminDO);
    }

    @Override
    public void updateAdminStatus(Long adminId, Integer status) {
        vipAdminAuthSupport.requireSuper();
        if (adminId == null || status == null || (status != 0 && status != 1)) {
            throw new ClientException("参数不正确");
        }
        if (adminId.equals(VipAdminContext.currentAdminId()) && status == 0) {
            // 防呆：把自己禁用会导致"没人能再进后台"
            throw new ClientException("不能禁用自己的账号");
        }
        VipAdminDO existDO = vipAdminMapper.selectById(adminId);
        if (existDO == null || (existDO.getDelFlag() != null && existDO.getDelFlag() != 0)) {
            throw new ClientException("管理员不存在");
        }
        VipAdminDO updateDO = new VipAdminDO();
        updateDO.setId(adminId);
        updateDO.setStatus(status);
        if (vipAdminMapper.updateById(updateDO) < 1) {
            throw new ClientException("管理员状态更新失败");
        }
        vipAdminAuthSupport.log("ADMIN_STATUS", "ADMIN", existDO.getUsername(), "状态=" + status);
    }

    @Override
    public List<VipAdminOpLogDO> listOpLogs(Integer limit) {
        vipAdminAuthSupport.requireSuper();
        int size = limit == null || limit <= 0 ? OP_LOG_DEFAULT_LIMIT : Math.min(limit, OP_LOG_MAX_LIMIT);
        return vipAdminOpLogMapper.selectList(Wrappers.lambdaQuery(VipAdminOpLogDO.class)
                .orderByDesc(VipAdminOpLogDO::getId)
                .last("limit " + size));
    }

    private VipAdminDO requireCurrentAdmin() {
        VipAdminDO adminDO = VipAdminContext.get();
        if (adminDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_ADMIN_UNAUTHORIZED);
        }
        return adminDO;
    }

    private VipAdminRespDTO toAdminRespDTO(VipAdminDO adminDO) {
        VipAdminRespDTO respDTO = BeanUtil.toBean(adminDO, VipAdminRespDTO.class);
        respDTO.setRoleDesc(VipAdminRoleEnum.descOf(adminDO.getRole()));
        return respDTO;
    }

    private Date parseTime(String time, String fieldName) {
        if (StrUtil.isBlank(time)) {
            return null;
        }
        try {
            return DateUtil.parse(time.trim());
        } catch (Exception e) {
            throw new ClientException(fieldName + "格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}