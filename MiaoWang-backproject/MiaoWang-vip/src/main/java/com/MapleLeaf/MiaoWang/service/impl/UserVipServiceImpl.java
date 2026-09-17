package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.dto.req.VipOrderCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipQuotaRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipBizTypeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPayChannelEnum;
import com.MapleLeaf.MiaoWang.service.UserVipService;
import com.MapleLeaf.MiaoWang.service.VipOrderService;
import com.MapleLeaf.MiaoWang.service.VipQuotaService;
import com.MapleLeaf.MiaoWang.support.VipBenefit;
import com.MapleLeaf.MiaoWang.support.VipBenefitResolver;
import com.MapleLeaf.MiaoWang.support.VipUserHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户会员服务
 *
 * <p>下单/支付/退款等订单写操作已全部搬到 {@link VipOrderService}（那里是完整的支付状态机），
 * 这里只留「权益总览」与兼容用的「一键开通」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserVipServiceImpl implements UserVipService {

    private final VipBenefitResolver vipBenefitResolver;

    private final VipQuotaService vipQuotaService;

    private final VipOrderService vipOrderService;

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
        // 兼容旧接口「一键开通」：下单 + 模拟支付。
        // 注意它不是捷径：内部走的是同一套下单/回调状态机，只是把"渠道回调"用模拟渠道自己触发了。
        // 真实支付请用 POST /vip/order 下单，等渠道回调；本接口供前端联调与演示。
        VipOrderCreateReqDTO createRequest = new VipOrderCreateReqDTO();
        createRequest.setPlanCode(requestParam.getPlanCode());
        createRequest.setPayType(VipPayChannelEnum.MOCK.getCode());
        VipOrderRespDTO order = vipOrderService.createOrder(createRequest);
        return vipOrderService.mockPay(order.getOrderNo());
    }

    /**
     * 组装权益总览：权益上限走解析器，剩余次数复用额度服务，保证「展示口径 = 扣减口径」
     */
    private UserVipBenefitRespDTO buildBenefit(Long uid) {
        VipBenefit benefit = vipBenefitResolver.resolve(uid);
        UserVipBenefitRespDTO respDTO = BeanUtil.toBean(benefit, UserVipBenefitRespDTO.class);
        VipQuotaRespDTO ragQuota = vipQuotaService.queryQuota(uid, VipBizTypeEnum.RAG.getBiz());
        VipQuotaRespDTO chatQuota = vipQuotaService.queryQuota(uid, VipBizTypeEnum.CHAT.getBiz());
        respDTO.setRagPeriodDesc(ragQuota.getPeriodDesc());
        respDTO.setRagUsed(ragQuota.getUsed());
        respDTO.setRagRemaining(ragQuota.getRemaining());
        respDTO.setChatPeriodDesc(chatQuota.getPeriodDesc());
        respDTO.setChatUsed(chatQuota.getUsed());
        respDTO.setChatRemaining(chatQuota.getRemaining());
        return respDTO;
    }
}