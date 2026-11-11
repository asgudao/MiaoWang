package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.constant.VipScopeConstant;
import com.MapleLeaf.MiaoWang.dto.req.VipOrderCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipSubscribeReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserVipBenefitRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipPetBenefitRespDTO;
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
    public VipPetBenefitRespDTO getPetBenefitByUid(Long uid, Long pid) {
        return buildPetBenefit(uid, pid);
    }

    @Override
    public VipPetBenefitRespDTO getMyPetBenefit(Long pid) {
        return buildPetBenefit(VipUserHelper.currentUid(), pid);
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

    /**
     * 组装「某一只宠物」的宠物级权益
     *
     * <p>两个标记各回答一个问题，刻意不合并：
     * {@code petVip} = 这只宠物身上有没有宠物级档位（如宠物纪念年包）；
     * {@code userVip} = 这个用户有没有用户级会员。产品硬规则⑤：宠物纪念包<b>独立于</b>用户级 VIP，
     * 合并成一个 true/false 之后，「用户开了月卡但没给这只宠物买纪念包」就没法表达了。
     *
     * <p>pid 收敛成哨兵值 0 时（未指定/非法），直接返回 {@code petVip=false}：
     * 调用方拿到的就是「没有宠物级权益」，而不是一个看起来像用户级权益的东西。
     */
    private VipPetBenefitRespDTO buildPetBenefit(Long uid, Long pid) {
        long normalizedPid = VipScopeConstant.normalize(pid);
        VipBenefitResolver.PetScope petScope = vipBenefitResolver.resolvePetScope(uid, normalizedPid);
        VipPetBenefitRespDTO respDTO = new VipPetBenefitRespDTO();
        respDTO.setUid(uid);
        respDTO.setPid(normalizedPid);
        respDTO.setPetVip(petScope.petVip());
        respDTO.setPetPlanNames(petScope.planNames());
        respDTO.setPetExpireTime(petScope.expireTime());
        respDTO.setUserVip(vipBenefitResolver.resolve(uid).getVip());
        return respDTO;
    }
}