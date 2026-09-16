package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.UserVipDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipOrderDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPlanDO;
import com.MapleLeaf.MiaoWang.dao.mapper.UserVipMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipOrderMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPlanMapper;
import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipBizTypeEnum;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.service.UserVipService;
import com.MapleLeaf.MiaoWang.service.VipQuotaService;
import com.MapleLeaf.MiaoWang.support.VipBenefit;
import com.MapleLeaf.MiaoWang.support.VipBenefitResolver;
import com.MapleLeaf.MiaoWang.support.VipUserHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserVipServiceImpl implements UserVipService {

    /**
     * 支付方式：当前为模拟支付，接入真实支付后改为 WECHAT/ALIPAY
     */
    private static final String PAY_TYPE_MOCK = "MOCK";

    /**
     * 支付状态：已支付
     */
    private static final int PAY_STATUS_PAID = 1;

    private final VipPlanMapper vipPlanMapper;

    private final UserVipMapper userVipMapper;

    private final VipOrderMapper vipOrderMapper;

    private final VipBenefitResolver vipBenefitResolver;

    private final VipQuotaService vipQuotaService;

    @Override
    public UserVipBenefitRespDTO getMyBenefit() {
        return buildBenefit(VipUserHelper.currentUid());
    }

    @Override
    public UserVipBenefitRespDTO getBenefitByUid(Long uid) {
        return buildBenefit(uid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO subscribe(VipSubscribeReqDTO requestParam) {
        if (requestParam == null || StrUtil.isBlank(requestParam.getPlanCode())) {
            throw new ClientException("请选择要开通的会员档位");
        }
        VipPlanDO planDO = vipPlanMapper.selectOne(Wrappers.lambdaQuery(VipPlanDO.class)
                .eq(VipPlanDO::getPlanCode, requestParam.getPlanCode())
                .eq(VipPlanDO::getDelFlag, 0)
                .last("limit 1"));
        if (planDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_NOT_EXIST);
        }
        if (planDO.getPurchasable() == null || planDO.getPurchasable() != 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_NOT_PURCHASABLE);
        }
        Long uid = VipUserHelper.currentUid();
        String username = VipUserHelper.currentUsername();
        Date now = new Date();
        // 续费口径：同一档位只保留一行，未过期就在原到期时间上叠加，已过期则从现在开始算
        UserVipDO existMembership = userVipMapper.selectOne(Wrappers.lambdaQuery(UserVipDO.class)
                .eq(UserVipDO::getUid, uid)
                .eq(UserVipDO::getPlanCode, planDO.getPlanCode())
                .eq(UserVipDO::getDelFlag, 0)
                .last("limit 1"));
        Date startTime;
        Date expireTime;
        if (existMembership != null) {
            Date baseTime = existMembership.getExpireTime() != null && existMembership.getExpireTime().after(now)
                    ? existMembership.getExpireTime()
                    : now;
            startTime = existMembership.getStartTime();
            expireTime = DateUtil.offsetDay(baseTime, planDO.getDurationDays());
            existMembership.setExpireTime(expireTime);
            existMembership.setStatus(1);
            if (userVipMapper.updateById(existMembership) < 1) {
                throw new ClientException(VipErrorCodeEnum.VIP_MEMBERSHIP_SAVE_ERROR);
            }
        } else {
            startTime = now;
            expireTime = DateUtil.offsetDay(now, planDO.getDurationDays());
            UserVipDO membership = new UserVipDO();
            membership.setUid(uid);
            membership.setUsername(username);
            membership.setPlanCode(planDO.getPlanCode());
            membership.setStartTime(startTime);
            membership.setExpireTime(expireTime);
            membership.setStatus(1);
            if (userVipMapper.insert(membership) < 1) {
                throw new ClientException(VipErrorCodeEnum.VIP_MEMBERSHIP_SAVE_ERROR);
            }
        }
        // 订单落库：金额与档位名做快照，档位以后改价/改名不影响历史订单
        // TODO 真实支付接入点：这里改成先落「待支付」订单，支付回调里再置为已支付并开通会员；
        //      高并发下的重复下单/重复回调需要幂等控制（分布式锁或唯一索引 + 状态机），后续专门做
        VipOrderDO orderDO = new VipOrderDO();
        orderDO.setOrderNo(generateOrderNo());
        orderDO.setUid(uid);
        orderDO.setUsername(username);
        orderDO.setPlanCode(planDO.getPlanCode());
        orderDO.setPlanName(planDO.getPlanName());
        orderDO.setAmount(planDO.getPrice());
        orderDO.setPayType(PAY_TYPE_MOCK);
        orderDO.setPayStatus(PAY_STATUS_PAID);
        orderDO.setPayTime(now);
        orderDO.setStartTime(startTime);
        orderDO.setExpireTime(expireTime);
        if (vipOrderMapper.insert(orderDO) < 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_SAVE_ERROR);
        }
        log.info("用户 {}（uid={}）开通档位 {}，到期时间 {}", username, uid, planDO.getPlanCode(), expireTime);
        return BeanUtil.toBean(orderDO, VipOrderRespDTO.class);
    }

    @Override
    public List<VipOrderRespDTO> listMyOrders() {
        Long uid = VipUserHelper.currentUid();
        List<VipOrderDO> orderDOList = vipOrderMapper.selectList(Wrappers.lambdaQuery(VipOrderDO.class)
                .eq(VipOrderDO::getUid, uid)
                .eq(VipOrderDO::getDelFlag, 0)
                .orderByDesc(VipOrderDO::getCreateTime));
        if (orderDOList == null || orderDOList.isEmpty()) {
            return List.of();
        }
        return orderDOList.stream()
                .map(orderDO -> BeanUtil.toBean(orderDO, VipOrderRespDTO.class))
                .collect(Collectors.toList());
    }

    /**
     * 组装权益总览：权益上限走解析器，剩余次数复用额度服务，保证「展示口径 = 扣减口径」
     */
    private UserVipBenefitRespDTO buildBenefit(Long uid) {
        VipBenefit benefit = vipBenefitResolver.resolve(uid);
        UserVipBenefitRespDTO respDTO = BeanUtil.toBean(benefit, UserVipBenefitRespDTO.class);
        VipQuotaRespDTO ragQuota = vipQuotaService.queryQuota(uid, VipBizTypeEnum.RAG.getBiz());
        VipQuotaRespDTO chatQuota = vipQuotaService.queryQuota(uid, VipBizTypeEnum.CHAT.getBiz());
        respDTO.setRagUsedToday(ragQuota.getUsed());
        respDTO.setRagRemainingToday(ragQuota.getRemaining());
        respDTO.setChatUsedToday(chatQuota.getUsed());
        respDTO.setChatRemainingToday(chatQuota.getRemaining());
        return respDTO;
    }

    private String generateOrderNo() {
        return "VIP" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
    }
}