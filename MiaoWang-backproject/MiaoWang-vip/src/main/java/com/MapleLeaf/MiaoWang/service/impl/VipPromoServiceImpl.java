package com.MapleLeaf.MiaoWang.service.impl;


import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dao.entity.VipPlanDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPromoCodeDO;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPlanMapper;
import com.MapleLeaf.MiaoWang.dto.req.VipPromoGrantReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipPromoRedeemReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPromoTypeEnum;
import com.MapleLeaf.MiaoWang.service.VipOrderService;
import com.MapleLeaf.MiaoWang.service.VipPromoService;
import com.MapleLeaf.MiaoWang.support.VipPromoCodeSupport;
import com.MapleLeaf.MiaoWang.support.VipUserHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 促销码核销
 *
 * <h3>顺序为什么要「先发权益、后占名额」</h3>
 * 名额是<b>概率性约束</b>（并发下 CAS 可能没抢到），而"用户已经核销成功"是<b>既成事实</b>。
 * 如果先占名额再发权益，中途失败就会出现"名额扣了、会员没发"——用户投诉且无法自证；
 * 反过来最坏情况只是超发一次，记 error 日志就能对账追责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipPromoServiceImpl implements VipPromoService {

    private final VipPromoCodeSupport vipPromoCodeSupport;

    private final VipPlanMapper vipPlanMapper;

    private final VipOrderService vipOrderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO redeem(VipPromoRedeemReqDTO requestParam) {
        Long uid = VipUserHelper.currentUid();
        String username = VipUserHelper.currentUsername();
        VipPromoCodeDO promoCode = vipPromoCodeSupport.loadUsableCode(
                requestParam == null ? null : requestParam.getCode());
        if (VipPromoTypeEnum.of(promoCode.getPromoType()) != VipPromoTypeEnum.PLAN) {
            // 立减码不该在这里核销：它在下单时抵扣金额，支付成功才占名额
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_TYPE_MISMATCH);
        }
        // 每人限一次：这里先查一次给出友好提示，真正的保证在核销记录的唯一索引上
        if (vipPromoCodeSupport.findRedeem(promoCode.getId(), uid) != null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_USED_BY_ME);
        }
        VipPlanDO planDO = vipPlanMapper.selectOne(Wrappers.lambdaQuery(VipPlanDO.class)
                .eq(VipPlanDO::getPlanCode, promoCode.getPlanCode())
                .eq(VipPlanDO::getDelFlag, 0)
                .last("limit 1"));
        if (planDO == null) {
            // 配置问题：码指的档位不存在。这属于后台配错，不能让用户看到"兑换码无效"背锅
            log.error("促销码 {} 配置的档位 {} 不存在", promoCode.getCode(), promoCode.getPlanCode());
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_NOT_EXIST);
        }
        if (promoCode.getDurationDays() == null || promoCode.getDurationDays() <= 0) {
            log.error("促销码 {} 没有配置赠送天数", promoCode.getCode());
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_NOT_EXIST);
        }
        // ① 先生成 0 元促销订单并发会员（同一事务）；② 再占名额
        VipOrderRespDTO order = vipOrderService.createPromoOrder(VipPromoGrantReqDTO.of(
                uid, username, planDO.getPlanCode(), planDO.getPlanName(),
                promoCode.getDurationDays(), promoCode.getId(), promoCode.getCode()));
        boolean consumed = vipPromoCodeSupport.consume(promoCode, uid, username, order.getOrderNo());
        log.info("用户 {} 核销促销码 {}（{}），获得档位 {} {} 天，订单 {}，占用名额={}",
                uid, promoCode.getCode(), promoCode.getName(), planDO.getPlanCode(),
                promoCode.getDurationDays(), order.getOrderNo(), consumed);
        return order;
    }
}