package com.MapleLeaf.MiaoWang.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.config.VipPayProperties;
import com.MapleLeaf.MiaoWang.constant.VipScopeConstant;
import com.MapleLeaf.MiaoWang.dao.entity.UserVipDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipOrderDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPlanDO;
import com.MapleLeaf.MiaoWang.dao.entity.VipPromoCodeDO;
import com.MapleLeaf.MiaoWang.dao.mapper.UserVipMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipOrderMapper;
import com.MapleLeaf.MiaoWang.dao.mapper.VipPlanMapper;
import com.MapleLeaf.MiaoWang.dto.req.VipOrderCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipPromoGrantReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipRefundReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetFeignRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPayChannelEnum;
import com.MapleLeaf.MiaoWang.enums.VipPayStatusEnum;
import com.MapleLeaf.MiaoWang.enums.VipPromoTypeEnum;
import com.MapleLeaf.MiaoWang.pay.MockPayChannel;
import com.MapleLeaf.MiaoWang.pay.PayChannel;
import com.MapleLeaf.MiaoWang.pay.PayChannelRegistry;
import com.MapleLeaf.MiaoWang.pay.PayNotifyResult;
import com.MapleLeaf.MiaoWang.pay.PayQueryResult;
import com.MapleLeaf.MiaoWang.pay.PayRefundResult;
import com.MapleLeaf.MiaoWang.service.VipOrderService;
import com.MapleLeaf.MiaoWang.support.MemorialGateChecker;
import com.MapleLeaf.MiaoWang.support.VipPaySignUtil;
import com.MapleLeaf.MiaoWang.support.VipPetOwnershipChecker;
import com.MapleLeaf.MiaoWang.support.VipPromoCodeSupport;
import com.MapleLeaf.MiaoWang.support.VipUserHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会员订单服务：下单 → 支付回调 → 发会员 → 退款回收时长（含促销码抵扣与核销）
 *
 * <h3>一、为什么"下单"和"发会员"必须分开</h3>
 * 原实现是「下单即生效」（模拟支付），一旦接真实支付就会出现"没给钱先给会员"。
 * 现在 {@link #createOrder} 只落「待支付」，只有回调把订单 CAS 改成「已支付」的那一次才发会员，
 * 且发会员与状态流转在同一事务里 —— 不会出现"订单已支付但没发会员"的脏状态。
 *
 * <h3>二、幂等（高并发下最容易出事的地方）</h3>
 * <ul>
 *   <li><b>重复下单</b>：同一 {@code requestId} 唯一索引兜底 + 复用同一档位、同一优惠码的未超时待支付单</li>
 *   <li><b>重复回调</b>：状态已是「已支付」直接应答成功；并发时靠
 *       {@code update ... where pay_status=0} 的 CAS，只有一条能改成功，也就只有一次发会员。
 *       <b>不用分布式锁</b>：一条带条件的 UPDATE 就够，且天然跨实例生效</li>
 *   <li><b>重复退款</b>：同样 CAS（{@code where pay_status=1}），不会重复扣减会员时长</li>
 *   <li><b>优惠码超发</b>：看 {@link #handleNotify} 第 ⑧ 步 —— 名额是概率性约束，权益是既成事实</li>
 * </ul>
 *
 * <h3>三、回调校验顺序（顺序错了会出事）</h3>
 * 验签 → 时间戳新鲜度 → 找订单 → 已是已支付就幂等应答（<b>必须在 nonce 校验之前</b>，
 * 否则渠道正常的重复回调会被当成重放拒掉，渠道会一直重试）→ 金额校验（按<b>应付金额</b>）→
 * nonce 一次性 → CAS → 发会员 → 核销优惠码。
 *
 * <h3>四、主动查单与回调共用同一条落地链路</h3>
 * 回调会丢（公网抖动、渠道重试耗尽、开发机在局域网内收不到），所以 {@link #syncOrder} 允许
 * <b>订单本人</b>让服务端主动去问渠道。查到已支付就收敛成与回调同形状的 {@code PayNotifyResult}，
 * 走同一个 {@link #applyPaidResult} —— 金额校验、CAS、发会员、核销码只有一份实现，
 * 两条路径不会算出不一样的到期时间。查单路径没有可重放的渠道回调，所以跳过 nonce 校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VipOrderServiceImpl implements VipOrderService {

    /**
     * 回调 nonce 的一次性标记 key：同一个 nonce 只允许命中一次
     */
    private static final String NONCE_KEY_PREFIX = "MiaoWang:vip:pay:nonce:";

    /**
     * 档位时长兜底值（老订单没存 duration_days 时用）
     */
    private static final int DEFAULT_DURATION_DAYS = 30;

    /**
     * 促销单的支付方式标记。
     *
     * <p>刻意不加进 {@code VipPayChannelEnum}：它不是可收款渠道，没有也不该有渠道实现，
     * 加进去反而会让"退款时按渠道找实现"这种路径误命中。
     */
    private static final String PAY_TYPE_PROMO = "PROMO";

    /**
     * 真实渠道可支付的最小金额：立减不能把订单减到 0（渠道不支持 0 元支付）
     */
    private static final BigDecimal MIN_PAYABLE_AMOUNT = new BigDecimal("0.01");

    private final VipPlanMapper vipPlanMapper;

    private final VipOrderMapper vipOrderMapper;

    private final UserVipMapper userVipMapper;

    private final PayChannelRegistry payChannelRegistry;

    private final VipPayProperties vipPayProperties;

    private final StringRedisTemplate stringRedisTemplate;

    private final VipPromoCodeSupport vipPromoCodeSupport;

    private final VipPetOwnershipChecker vipPetOwnershipChecker;

    private final MemorialGateChecker memorialGateChecker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO createOrder(VipOrderCreateReqDTO requestParam) {
        if (requestParam == null || StrUtil.isBlank(requestParam.getPlanCode())) {
            throw new ClientException("请选择要开通的会员档位");
        }
        Long uid = VipUserHelper.currentUid();
        String username = VipUserHelper.currentUsername();
        // 宠物级（per-pet）下单：带 pid 就是「给这一只宠物买」（如宠物纪念年包），不带就是用户级。
        // 空/非正数一律收敛成 0，绝不让 NULL 进唯一键列（见 VipScopeConstant）
        long pid = VipScopeConstant.normalize(requestParam.getPid());
        VipPlanDO planDO = loadPurchasablePlan(requestParam.getPlanCode());
        // 档位作用域必须和 pid 对得上：用户级档位绑宠物、宠物级档位不绑宠物，两种都是脏数据
        validatePlanScope(planDO, pid);
        PayChannel channel = payChannelRegistry.require(
                StrUtil.blankToDefault(requestParam.getPayType(), VipPayChannelEnum.MOCK.getCode()));
        // 优惠码：这里只做「能不能用 + 减多少」的试算，名额等支付成功再占（用码不付款不该浪费名额）
        VipPromoCodeDO promoCode = StrUtil.isBlank(requestParam.getPromoCode())
                ? null
                : loadDiscountCode(requestParam.getPromoCode(), planDO);
        BigDecimal discountAmount = promoCode == null
                ? BigDecimal.ZERO
                : calcDiscountAmount(promoCode, planDO.getPrice());
        // 先把自己已超时的待支付单关掉，避免"上一单还挂着、这一单又建出来"
        closeExpiredOrders(uid);
        // 幂等①：同一 requestId 只认一单（弱网重试、用户连点都由它兜住）
        if (StrUtil.isNotBlank(requestParam.getRequestId())) {
            VipOrderDO existOrder = findByRequestId(uid, requestParam.getRequestId());
            if (existOrder != null) {
                log.info("requestId={} 重复下单，直接返回已有订单 {}", requestParam.getRequestId(), existOrder.getOrderNo());
                return toRespDTO(existOrder, channel.createPayment(existOrder, requestParam.getPayScene()));
            }
        }
        // 宠物级档位的前置校验。刻意排在这两处之后：
        //   · 放在 requestId 幂等之后 —— 重复提交同一个 requestId 要能拿回自己的订单，
        //     不能因为"这会儿已经买过了"就把一次正常重试变成报错
        //   · 放在待支付单复用之前 —— 已经买过还去复用那张待支付单，用户付完钱才发现拿不到东西
        if (VipScopeConstant.isPetLevel(pid)) {
            // ① 只能给自己的宠物买：否则能替别人的宠物"锁"一个纪念位，而权益发在自己账上。
            //    这一步顺带把宠物档案取回来，② 直接拿它判离世 —— 不为此多打一次远程调用
            PetFeignRespDTO pet = vipPetOwnershipChecker.requireOwnedIfPresent(pid);
            // ② 离世闸门（方案 D）：判据是 pet 的 life_status，随归属校验一起到手。
            //    刻意只在下单这一处判 —— 回调路径必须能在渠道重试下稳定成功，不能塞远程调用；
            //    而"已离世"没有回退，订单创建后不会反过来变回在世
            memorialGateChecker.requireDeceased(pet);
            // ③ 一只宠物最多买一年、不可续：这里刻意不看是否过期 ——
            //    到期后也不再卖第二次，"再买一年"会破坏那条 365 天的疗愈弧线（产品硬规则③）
            if (findMembership(uid, pid, planDO.getPlanCode()) != null) {
                log.warn("用户 {} 已为宠物 {} 购买过档位 {}，拒绝重复购买", uid, pid, planDO.getPlanCode());
                throw new ClientException(VipErrorCodeEnum.VIP_PET_PLAN_ALREADY_OWNED);
            }
        }
        // 幂等②：同一档位 + 同一宠物 + 同一优惠码还有未超时的待支付单就直接复用（用户连点不会出一堆单）
        VipOrderDO pendingOrder = findPendingOrder(uid, pid, planDO.getPlanCode(),
                promoCode == null ? null : promoCode.getId());
        if (pendingOrder != null) {
            log.info("用户 {} 在档位 {} 已有待支付订单 {}，复用该订单", uid, planDO.getPlanCode(), pendingOrder.getOrderNo());
            return toRespDTO(pendingOrder, channel.createPayment(pendingOrder, requestParam.getPayScene()));
        }
        // 到这里才需要拦"这个码我已经用过了"：如果只是重复点击，上面复用的分支已经把它接住了
        if (promoCode != null && vipPromoCodeSupport.findRedeem(promoCode.getId(), uid) != null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_USED_BY_ME);
        }
        VipOrderDO orderDO = new VipOrderDO();
        orderDO.setOrderNo(generateOrderNo());
        orderDO.setUid(uid);
        orderDO.setUsername(username);
        // 订单必须记住是给哪只宠物买的：发会员、退款回收时长都要按这个键找记录，
        // 丢了它退给宠物 A 的款会去扣宠物 B 的时长（挂账 #94 的另一半）
        orderDO.setPid(pid);
        orderDO.setPlanCode(planDO.getPlanCode());
        orderDO.setPlanName(planDO.getPlanName());
        orderDO.setAmount(planDO.getPrice());
        orderDO.setDiscountAmount(discountAmount);
        orderDO.setPayableAmount(planDO.getPrice().subtract(discountAmount).setScale(2, RoundingMode.HALF_UP));
        orderDO.setPromoCodeId(promoCode == null ? null : promoCode.getId());
        orderDO.setPromoCode(promoCode == null ? null : promoCode.getCode());
        // 时长快照：档位以后改时长不影响已售订单（退款回收时长也按这个数）
        orderDO.setDurationDays(planDO.getDurationDays());
        orderDO.setPayType(channel.code());
        orderDO.setPayStatus(VipPayStatusEnum.PENDING.getCode());
        orderDO.setRequestId(requestParam.getRequestId());
        orderDO.setNotifyCount(0);
        // 本项目约定：BaseDO 没有 @TableLogic，逻辑删除标记由实现显式写
        orderDO.setDelFlag(0);
        try {
            if (vipOrderMapper.insert(orderDO) < 1) {
                throw new ClientException(VipErrorCodeEnum.VIP_ORDER_SAVE_ERROR);
            }
        } catch (DuplicateKeyException e) {
            // 并发下两个请求带同一个 requestId 撞唯一索引：另一条已经建好了，取回它返回
            VipOrderDO existOrder = findByRequestId(uid, requestParam.getRequestId());
            if (existOrder == null) {
                throw e;
            }
            log.info("requestId={} 并发下单撞唯一索引，返回已有订单 {}", requestParam.getRequestId(), existOrder.getOrderNo());
            return toRespDTO(existOrder, channel.createPayment(existOrder, requestParam.getPayScene()));
        }
        log.info("用户 {}（uid={}）创建会员订单 {}：档位 {}，pid={}，原价 {}，优惠 {}，应付 {}，渠道 {}，场景 {}，优惠码 {}",
                username, uid, orderDO.getOrderNo(), planDO.getPlanCode(), pid, orderDO.getAmount(),
                orderDO.getDiscountAmount(), orderDO.getPayableAmount(), channel.code(),
                StrUtil.blankToDefault(requestParam.getPayScene(), "渠道默认"), orderDO.getPromoCode());
        return toRespDTO(orderDO, channel.createPayment(orderDO, requestParam.getPayScene()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO mockPay(String orderNo) {
        VipOrderDO orderDO = requireMyOrder(orderNo);
        if (VipPayStatusEnum.of(orderDO.getPayStatus()) != VipPayStatusEnum.PENDING) {
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_STATUS_ERROR);
        }
        // 刻意走完整回调链路（造签名参数 → handleNotify）：这样联调测过的路径就是真实渠道要走的路径
        MockPayChannel mockChannel = (MockPayChannel) payChannelRegistry.require(VipPayChannelEnum.MOCK.getCode());
        handleNotify(VipPayChannelEnum.MOCK.getCode(), mockChannel.buildNotifyParams(orderDO));
        return toRespDTO(requireMyOrder(orderNo), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleNotify(String channelCode, Map<String, String> params) {
        PayChannel channel = payChannelRegistry.require(channelCode);
        // ① 验签 + 时间戳新鲜度：验不过直接抛，绝不能"验不过也放过"
        PayNotifyResult notifyResult = channel.verifyAndParse(params);
        // 回调路径必须带 nonce：用空白兜住"渠道压根没给 nonce"的情况，
        // 让它被 requireFreshNonce 拦下（而不是被当成"服务端主动查单"跳过防重放）
        return applyPaidResult(channel, notifyResult,
                StrUtil.blankToDefault(params.get(VipPaySignUtil.NONCE_FIELD), StrUtil.EMPTY));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO syncOrder(String orderNo) {
        VipOrderDO orderDO = requireMyOrder(orderNo);
        // 状态机已有定论的单子（已支付/已关闭/已退款）查单没有意义，直接回读当前状态：
        // 前端支付后反复点"对账"也不会重复发会员
        if (VipPayStatusEnum.of(orderDO.getPayStatus()) != VipPayStatusEnum.PENDING) {
            log.info("订单 {} 当前状态【{}】，主动查单按幂等回读", orderNo,
                    VipPayStatusEnum.descOf(orderDO.getPayStatus()));
            return toRespDTO(orderDO, null);
        }
        PayChannel channel = payChannelRegistry.require(orderDO.getPayType());
        PayQueryResult queryResult = channel.queryTrade(orderDO.getOrderNo());
        if (queryResult == null) {
            // 渠道没实现查单（如 mock）：如实回读，不猜、不假装成功
            log.info("订单 {} 的渠道 {} 不支持主动查单，回读当前状态", orderNo, orderDO.getPayType());
            return toRespDTO(orderDO, null);
        }
        if (!queryResult.paid()) {
            log.info("订单 {} 主动查单：渠道状态 {}，尚未支付", orderNo, queryResult.tradeStatus());
            return toRespDTO(orderDO, null);
        }
        // ② 复用回调那条链路（金额校验 → CAS → 发会员 → 核销优惠码）：
        //    nonce 传 null 表示"这是服务端自己查出来的结果，没有可重放的渠道回调"，
        //    金额与幂等仍然由同一份代码把关
        log.info("订单 {} 主动查单：渠道判定已支付（tradeStatus={}，channelTradeNo={}），补走回调链路发货",
                orderNo, queryResult.tradeStatus(), queryResult.channelTradeNo());
        applyPaidResult(channel, queryResult.toNotifyResult(), null);
        // 回读一次：状态与会员区间都已变，直接返回旧对象前端会以为没生效
        return toRespDTO(findByOrderNo(orderNo), null);
    }

    /**
     * 「渠道说钱到了」之后的统一落地路径：<b>回调与主动查单共用</b>
     *
     * <p>抽出来的理由：{@link #handleNotify}（渠道推给我们）与 {@link #syncOrder}（我们主动去问）
     * 必须走<b>同一份</b>金额校验、CAS 状态流转、发会员、核销优惠码的代码 —— 两条路径各写一遍，
     * 迟早会出现"回调发的会员和查单发的会员到期时间不一样"这种对不上的账。
     *
     * @param channel      渠道实现（取 ackBody、判要不要 nonce）
     * @param notifyResult 渠道告知的支付结果（回调解析出来 / 查单收敛出来）
     * @param nonce        回调 nonce（防重放）；主动查单没有可重放的渠道回调，传 null
     */
    private String applyPaidResult(PayChannel channel, PayNotifyResult notifyResult, String nonce) {
        VipOrderDO orderDO = findByOrderNo(notifyResult.orderNo());
        if (orderDO == null) {
            log.warn("支付结果找不到订单：channel={}，orderNo={}", channel.code(), notifyResult.orderNo());
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_NOT_EXIST);
        }
        // 渠道明确告知支付失败：不动状态，用户在有效期内可以重新支付
        if (!notifyResult.success()) {
            log.warn("支付结果为失败状态：orderNo={}，原因={}", notifyResult.orderNo(), notifyResult.failReason());
            return channel.ackBody();
        }
        VipPayStatusEnum payStatus = VipPayStatusEnum.of(orderDO.getPayStatus());
        // 重复回调：直接应答成功（真实渠道会重试到收到成功应答为止）。
        // 这一步必须在 nonce 校验之前 —— 否则渠道的正常重试会被判成重放、不断重试下去。
        if (payStatus == VipPayStatusEnum.PAID) {
            log.info("订单 {} 已是已支付，重复回调按幂等应答（channelTradeNo={}）",
                    orderDO.getOrderNo(), notifyResult.channelTradeNo());
            return channel.ackBody();
        }
        if (payStatus != VipPayStatusEnum.PENDING) {
            // 已关闭/已退款不能再被改成已支付，否则等于凭空发会员。
            // ⚠️ 已关闭的单子在渠道侧真的付成功了属于"钱收了但没发会员"，
            //    日志里留清楚线索，只能人工退款（见 README 挂账）
            log.warn("订单 {} 当前状态【{}】不接受支付结果，渠道单号 {}",
                    orderDO.getOrderNo(), VipPayStatusEnum.descOf(orderDO.getPayStatus()),
                    notifyResult.channelTradeNo());
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_STATUS_ERROR);
        }
        // 金额必须和订单一致：有优惠码时比的是「应付金额」，不是原价。
        // 否则就是"改小金额骗会员"（真实渠道同样要校验）
        BigDecimal payableAmount = effectivePayableAmount(orderDO);
        if (notifyResult.amount() == null || payableAmount == null
                || notifyResult.amount().compareTo(payableAmount) != 0) {
            log.error("支付金额与订单不一致：orderNo={}，渠道={}，应付={}",
                    orderDO.getOrderNo(), notifyResult.amount(), payableAmount);
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_AMOUNT_MISMATCH);
        }
        // nonce 一次性：挡住"录下一条合法回调反复重放"。
        // 只有自带 nonce 的渠道需要（mock 的 HMAC 自签）；支付宝的异步通知没有 nonce 字段，
        // 它的重放防护是「已支付即幂等应答」+ 下面的 CAS，见 PayChannel#notifyNonceRequired
        if (channel.notifyNonceRequired()) {
            requireFreshNonce(nonce);
        }
        // CAS 状态流转：并发回调只有一条 update 能命中 pay_status=0，也就只有一次发会员
        Date now = new Date();
        int updated = vipOrderMapper.update(null, Wrappers.lambdaUpdate(VipOrderDO.class)
                .eq(VipOrderDO::getOrderNo, orderDO.getOrderNo())
                .eq(VipOrderDO::getPayStatus, VipPayStatusEnum.PENDING.getCode())
                .set(VipOrderDO::getPayStatus, VipPayStatusEnum.PAID.getCode())
                .set(VipOrderDO::getPayTime, now)
                .set(VipOrderDO::getChannelTradeNo, notifyResult.channelTradeNo())
                .set(VipOrderDO::getNotifyTime, now)
                .set(VipOrderDO::getNotifyCount,
                        (orderDO.getNotifyCount() == null ? 0 : orderDO.getNotifyCount()) + 1));
        if (updated < 1) {
            log.info("订单 {} 已被并发回调处理，本次按幂等忽略", orderDO.getOrderNo());
            return channel.ackBody();
        }
        // 发会员：与状态流转同一事务，任一步失败整体回滚，不会留下"已支付但没发会员"
        grantMembership(orderDO);
        // 核销「立减」优惠码：到这一步钱已经收了，所以名额没抢到也只记 error，不影响发会员
        if (orderDO.getPromoCodeId() != null) {
            vipPromoCodeSupport.consumeDiscountQuietly(orderDO.getPromoCodeId(), orderDO.getPromoCode(),
                    orderDO.getUid(), orderDO.getUsername(), orderDO.getOrderNo());
        }
        log.info("订单 {} 支付成功并发会员：uid={}，pid={}，档位={}，到期时间={}",
                orderDO.getOrderNo(), orderDO.getUid(), orderDO.getPid(), orderDO.getPlanCode(), orderDO.getExpireTime());
        return channel.ackBody();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO refund(String orderNo, VipRefundReqDTO requestParam) {
        return doRefund(requireMyOrder(orderNo), requestParam == null ? null : requestParam.getReason(), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO adminRefund(String orderNo, String reason) {
        VipOrderDO orderDO = findByOrderNo(orderNo);
        if (orderDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_NOT_EXIST);
        }
        return doRefund(orderDO, reason, false);
    }

    /**
     * 退款主流程：用户自助与运营代退共用
     *
     * @param selfService true=用户自助（校验 7 天窗口）；false=运营代退（不校验窗口，但要记操作日志）
     */
    private VipOrderRespDTO doRefund(VipOrderDO orderDO, String reason, boolean selfService) {
        // 0 元促销单没有钱可退，允许"退款"只会把刚发出去的会员又收回去
        if (orderDO.getAmount() == null || orderDO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_ORDER_NO_REFUND);
        }
        VipPayStatusEnum payStatus = VipPayStatusEnum.of(orderDO.getPayStatus());
        if (payStatus == VipPayStatusEnum.REFUNDED) {
            // 重复点退款：幂等返回，绝不能重复扣减会员时长
            log.info("订单 {} 已退款，重复请求按幂等处理", orderDO.getOrderNo());
            return toRespDTO(orderDO, null);
        }
        if (payStatus != VipPayStatusEnum.PAID) {
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_STATUS_ERROR);
        }
        // 自助退款窗口：超出窗口只能走人工（运营代退 selfService=false，不受此限）
        if (selfService && orderDO.getPayTime() != null
                && DateUtil.offsetDay(orderDO.getPayTime(), vipPayProperties.getRefundWindowDays()).before(new Date())) {
            throw new ClientException(VipErrorCodeEnum.VIP_REFUND_WINDOW_EXPIRED);
        }
        PayChannel channel = payChannelRegistry.require(orderDO.getPayType());
        PayRefundResult refundResult = channel.refund(orderDO, reason);
        if (!refundResult.success()) {
            // 渠道退款失败：订单保持已支付，用户可以再试（不能先改状态再退款）
            log.error("订单 {} 渠道退款失败：{}", orderDO.getOrderNo(), refundResult.failReason());
            throw new ClientException(VipErrorCodeEnum.VIP_REFUND_FAILED);
        }
        int updated = vipOrderMapper.update(null, Wrappers.lambdaUpdate(VipOrderDO.class)
                .eq(VipOrderDO::getOrderNo, orderDO.getOrderNo())
                .eq(VipOrderDO::getPayStatus, VipPayStatusEnum.PAID.getCode())
                .set(VipOrderDO::getPayStatus, VipPayStatusEnum.REFUNDED.getCode())
                .set(VipOrderDO::getRefundNo, refundResult.refundNo())
                .set(VipOrderDO::getRefundReason, reason)
                .set(VipOrderDO::getRefundTime, new Date()));
        if (updated < 1) {
            log.info("订单 {} 退款状态已被并发修改，本次按幂等忽略", orderDO.getOrderNo());
            return toRespDTO(findByOrderNo(orderDO.getOrderNo()), null);
        }
        revokeMembership(orderDO);
        // 回读一次：状态/退款时间已变，直接返回旧对象前端会以为没生效
        // 注意这里用 findByOrderNo 而不是 requireMyOrder：运营代退时操作人不是订单所有者
        return toRespDTO(findByOrderNo(orderDO.getOrderNo()), null);
    }

    @Override
    public List<VipOrderRespDTO> listOrdersForAdmin(Integer payStatus, String orderNo, int limit) {
        List<VipOrderDO> orderDOList = vipOrderMapper.selectList(Wrappers.lambdaQuery(VipOrderDO.class)
                .eq(payStatus != null, VipOrderDO::getPayStatus, payStatus)
                .eq(StrUtil.isNotBlank(orderNo), VipOrderDO::getOrderNo, orderNo)
                .eq(VipOrderDO::getDelFlag, 0)
                .orderByDesc(VipOrderDO::getCreateTime)
                .last("limit " + Math.max(1, limit)));
        if (orderDOList == null || orderDOList.isEmpty()) {
            return List.of();
        }
        return orderDOList.stream()
                .map(orderDO -> toRespDTO(orderDO, null))
                .collect(Collectors.toList());
    }

    @Override
    public List<VipOrderRespDTO> listMyOrders() {
        Long uid = VipUserHelper.currentUid();
        // 顺手关掉自己超时的待支付单：列表里不该长期挂着一堆早就付不了的订单
        closeExpiredOrders(uid);
        List<VipOrderDO> orderDOList = vipOrderMapper.selectList(Wrappers.lambdaQuery(VipOrderDO.class)
                .eq(VipOrderDO::getUid, uid)
                .eq(VipOrderDO::getDelFlag, 0)
                .orderByDesc(VipOrderDO::getCreateTime));
        if (orderDOList == null || orderDOList.isEmpty()) {
            return List.of();
        }
        return orderDOList.stream()
                .map(orderDO -> toRespDTO(orderDO, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VipOrderRespDTO createPromoOrder(VipPromoGrantReqDTO requestParam) {
        // 宠物级档位只能由「带 pid 的下单」发放：促销码是运营批量发的东西，它没有 pid 可言，
        // 一旦放行就会落成 pid=0 的行 —— 那只宠物的权益没发出去，
        // 反倒给这个用户凭空加了一份用户级权益（挂账 #96 那类静默放大的翻版）
        VipPlanDO promoPlan = findPlan(requestParam.getPlanCode());
        if (promoPlan != null && promoPlan.getPetScoped() != null && promoPlan.getPetScoped() == 1) {
            log.warn("促销码 {} 试图发放宠物级档位 {}，已拒绝（宠物级权益只能走带 pid 的下单）",
                    requestParam.getPromoCode(), requestParam.getPlanCode());
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_PET_SCOPE_MISMATCH);
        }
        Date now = new Date();
        int durationDays = requestParam.getDurationDays() == null
                ? DEFAULT_DURATION_DAYS : requestParam.getDurationDays();
        VipOrderDO orderDO = new VipOrderDO();
        orderDO.setOrderNo(generateOrderNo());
        orderDO.setUid(requestParam.getUid());
        orderDO.setUsername(requestParam.getUsername());
        orderDO.setPlanCode(requestParam.getPlanCode());
        orderDO.setPlanName(requestParam.getPlanName());
        // 0 元单：这里没有钱要收，订单的意义是「权益发放凭证」（有单号、有快照、可追溯）
        orderDO.setAmount(BigDecimal.ZERO);
        orderDO.setDiscountAmount(BigDecimal.ZERO);
        orderDO.setPayableAmount(BigDecimal.ZERO);
        orderDO.setDurationDays(durationDays);
        orderDO.setPayType(PAY_TYPE_PROMO);
        // id + code 都要落：id 供对账回连促销码表，code 是当时用的码的快照（漏了 id 就只能靠字符串对账）
        orderDO.setPromoCodeId(requestParam.getPromoCodeId());
        orderDO.setPromoCode(requestParam.getPromoCode());
        // 促销单直接落「已支付」：没有支付环节，也就没有回调
        orderDO.setPayStatus(VipPayStatusEnum.PAID.getCode());
        orderDO.setPayTime(now);
        orderDO.setNotifyCount(0);
        orderDO.setDelFlag(0);
        if (vipOrderMapper.insert(orderDO) < 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_SAVE_ERROR);
        }
        grantMembership(orderDO);
        log.info("促销码 {} 为 uid={} 发放档位 {} {} 天，促销订单 {}",
                requestParam.getPromoCode(), orderDO.getUid(), orderDO.getPlanCode(),
                durationDays, orderDO.getOrderNo());
        return toRespDTO(orderDO, null);
    }

    /**
     * 发放会员：同一「用户 + 宠物 + 档位」只保留一行，未过期就在原到期时间上叠加，已过期从当前时间算起
     *
     * <p>pid 必须参与定位（挂账 #94）：少了它，给宠物 B 买包会命中宠物 A 那一行 ——
     * A 白拿一年、B 什么都没得到，退款还会去扣 A 的时长。
     *
     * <p>同时把权益区间回写到订单（start/expire），退款回收时长时按订单上的口径扣。
     */
    private void grantMembership(VipOrderDO orderDO) {
        Date now = new Date();
        int durationDays = orderDO.getDurationDays() == null ? DEFAULT_DURATION_DAYS : orderDO.getDurationDays();
        UserVipDO membership = findMembership(orderDO.getUid(), orderDO.getPid(), orderDO.getPlanCode());
        Date startTime;
        Date expireTime;
        if (membership != null) {
            Date baseTime = membership.getExpireTime() != null && membership.getExpireTime().after(now)
                    ? membership.getExpireTime()
                    : now;
            startTime = membership.getStartTime() == null ? now : membership.getStartTime();
            expireTime = DateUtil.offsetDay(baseTime, durationDays);
            membership.setExpireTime(expireTime);
            membership.setStatus(1);
            if (userVipMapper.updateById(membership) < 1) {
                throw new ClientException(VipErrorCodeEnum.VIP_MEMBERSHIP_SAVE_ERROR);
            }
        } else {
            startTime = now;
            expireTime = DateUtil.offsetDay(now, durationDays);
            UserVipDO newMembership = new UserVipDO();
            newMembership.setUid(orderDO.getUid());
            newMembership.setUsername(orderDO.getUsername());
            // 归属维度落库（0 = 用户级哨兵值，真实宠物ID ≥ 1）
            newMembership.setPid(VipScopeConstant.normalize(orderDO.getPid()));
            newMembership.setPlanCode(orderDO.getPlanCode());
            newMembership.setStartTime(startTime);
            newMembership.setExpireTime(expireTime);
            newMembership.setStatus(1);
            if (userVipMapper.insert(newMembership) < 1) {
                throw new ClientException(VipErrorCodeEnum.VIP_MEMBERSHIP_SAVE_ERROR);
            }
        }
        vipOrderMapper.update(null, Wrappers.lambdaUpdate(VipOrderDO.class)
                .eq(VipOrderDO::getOrderNo, orderDO.getOrderNo())
                .set(VipOrderDO::getStartTime, startTime)
                .set(VipOrderDO::getExpireTime, expireTime));
        orderDO.setStartTime(startTime);
        orderDO.setExpireTime(expireTime);
    }

    /**
     * 退款后回收会员时长：按订单快照的时长扣减，剩余不足一次订阅时直接置为失效
     *
     * <p>注意不去动已消耗的额度计数（次数已经用掉了），这是产品口径上的取舍，已记在 README 挂账里。
     */
    private void revokeMembership(VipOrderDO orderDO) {
        UserVipDO membership = findMembership(orderDO.getUid(), orderDO.getPid(), orderDO.getPlanCode());
        if (membership == null || membership.getExpireTime() == null) {
            log.warn("订单 {} 退款：没有找到对应的会员记录，无需回收", orderDO.getOrderNo());
            return;
        }
        int durationDays = orderDO.getDurationDays() == null ? DEFAULT_DURATION_DAYS : orderDO.getDurationDays();
        Date now = new Date();
        Date newExpireTime = DateUtil.offsetDay(membership.getExpireTime(), -durationDays);
        if (!newExpireTime.after(now)) {
            // 剩余时长还不够这次订阅：直接失效，而不是留一段"负时长"
            newExpireTime = now;
            membership.setStatus(0);
        }
        membership.setExpireTime(newExpireTime);
        if (userVipMapper.updateById(membership) < 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_MEMBERSHIP_SAVE_ERROR);
        }
        log.info("订单 {} 退款：回收会员时长 {} 天，uid={}，pid={}，档位={}，新到期时间={}，会员状态={}",
                orderDO.getOrderNo(), durationDays, orderDO.getUid(), orderDO.getPid(), orderDO.getPlanCode(),
                newExpireTime, membership.getStatus());
    }

    /**
     * 关闭自己已超时的待支付订单（懒关单：不用定时任务，读列表/下单时顺手做）
     */
    private void closeExpiredOrders(Long uid) {
        Date deadline = DateUtil.offsetMinute(new Date(), -vipPayProperties.getPendingMinutes());
        int closed = vipOrderMapper.update(null, Wrappers.lambdaUpdate(VipOrderDO.class)
                .eq(VipOrderDO::getUid, uid)
                .eq(VipOrderDO::getPayStatus, VipPayStatusEnum.PENDING.getCode())
                .lt(VipOrderDO::getCreateTime, deadline)
                .set(VipOrderDO::getPayStatus, VipPayStatusEnum.CLOSED.getCode())
                .set(VipOrderDO::getCloseTime, new Date()));
        if (closed > 0) {
            log.info("用户 {} 有 {} 笔待支付订单超时未支付，已自动关单", uid, closed);
        }
    }

    /**
     * 下单时校验立减码（只校验可用性与适用范围，不占名额）
     */
    private VipPromoCodeDO loadDiscountCode(String promoCode, VipPlanDO planDO) {
        VipPromoCodeDO codeDO = vipPromoCodeSupport.loadUsableCode(promoCode);
        if (VipPromoTypeEnum.of(codeDO.getPromoType()) != VipPromoTypeEnum.DISCOUNT) {
            // 送会员时长的码要走 /vip/promo/redeem，不能在下单时当折扣用
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_TYPE_MISMATCH);
        }
        if (StrUtil.isNotBlank(codeDO.getPlanCode()) && !codeDO.getPlanCode().equals(planDO.getPlanCode())) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_NOT_APPLICABLE);
        }
        return codeDO;
    }

    /**
     * 计算立减金额：校验门槛，并把"减到 0 元"截断成 0.01（渠道不支持 0 元支付）
     */
    private BigDecimal calcDiscountAmount(VipPromoCodeDO promoCode, BigDecimal price) {
        BigDecimal minAmount = promoCode.getMinAmount() == null ? BigDecimal.ZERO : promoCode.getMinAmount();
        if (price.compareTo(minAmount) < 0) {
            throw new ClientException(VipErrorCodeEnum.VIP_PROMO_MIN_AMOUNT);
        }
        BigDecimal discountAmount = promoCode.getDiscountAmount() == null
                ? BigDecimal.ZERO : promoCode.getDiscountAmount();
        if (discountAmount.compareTo(price) >= 0) {
            discountAmount = price.subtract(MIN_PAYABLE_AMOUNT);
            log.info("优惠码 {} 立减 {} 不低于订单金额 {}，按 {} 截断（保留最小可支付金额）",
                    promoCode.getCode(), promoCode.getDiscountAmount(), price, discountAmount);
        }
        return discountAmount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 订单的应付金额：老订单没有 payable_amount（v3 之前下单即生效），回退成原价
     */
    private BigDecimal effectivePayableAmount(VipOrderDO orderDO) {
        return orderDO.getPayableAmount() == null ? orderDO.getAmount() : orderDO.getPayableAmount();
    }

    /**
     * nonce 一次性校验：Redis SETNX 成功才算首次，失败即重放
     */
    private void requireFreshNonce(String nonce) {
        if (StrUtil.isBlank(nonce)) {
            // 没有 nonce 就无法防重放，直接拒绝比放过安全
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(NONCE_KEY_PREFIX + nonce, "1",
                Duration.ofSeconds(vipPayProperties.getNotifySkewSeconds()));
        if (!Boolean.TRUE.equals(first)) {
            log.warn("支付回调 nonce 重复，判定为重放：{}", nonce);
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_REPLAY);
        }
    }

    private VipPlanDO loadPurchasablePlan(String planCode) {
        VipPlanDO planDO = findPlan(planCode);
        if (planDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_NOT_EXIST);
        }
        if (planDO.getPurchasable() == null || planDO.getPurchasable() != 1) {
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_NOT_PURCHASABLE);
        }
        return planDO;
    }

    /**
     * 按档位编码查档位配置
     *
     * <p>刻意不过滤 {@code purchasable}：促销发码路径要能识别出「这是宠物级档位」并拒绝它，
     * 那个档位恰好也是不可售的，用可售版查询会查不到、guard 直接失效。
     */
    private VipPlanDO findPlan(String planCode) {
        if (StrUtil.isBlank(planCode)) {
            return null;
        }
        return vipPlanMapper.selectOne(Wrappers.lambdaQuery(VipPlanDO.class)
                .eq(VipPlanDO::getPlanCode, planCode)
                .eq(VipPlanDO::getDelFlag, 0)
                .last("limit 1"));
    }

    /**
     * 校验档位作用域与下单请求是否匹配
     *
     * <p>两个方向都必须拦：
     * <ul>
     *   <li><b>宠物级档位没带 pid</b>：会落成 pid=0 的行，等于把「一只宠物专属」的东西
     *       塞进用户的总权益里（挂账 #96 那类静默放大的翻版）</li>
     *   <li><b>用户级档位带了 pid</b>：那只宠物会凭白多出一份它本来没有的用户级权益</li>
     * </ul>
     */
    private void validatePlanScope(VipPlanDO planDO, long pid) {
        boolean petScoped = planDO.getPetScoped() != null && planDO.getPetScoped() == 1;
        if (petScoped && !VipScopeConstant.isPetLevel(pid)) {
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_PET_REQUIRED);
        }
        if (!petScoped && VipScopeConstant.isPetLevel(pid)) {
            throw new ClientException(VipErrorCodeEnum.VIP_PLAN_PET_SCOPE_MISMATCH);
        }
    }

    private VipOrderDO findByOrderNo(String orderNo) {
        if (StrUtil.isBlank(orderNo)) {
            return null;
        }
        return vipOrderMapper.selectOne(Wrappers.lambdaQuery(VipOrderDO.class)
                .eq(VipOrderDO::getOrderNo, orderNo)
                .eq(VipOrderDO::getDelFlag, 0)
                .last("limit 1"));
    }

    /**
     * 查「我的」订单：查不到就是查不到，不区分"不存在"与"不是你的"，避免泄露他人订单号
     */
    private VipOrderDO requireMyOrder(String orderNo) {
        if (StrUtil.isBlank(orderNo)) {
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_NOT_EXIST);
        }
        VipOrderDO orderDO = vipOrderMapper.selectOne(Wrappers.lambdaQuery(VipOrderDO.class)
                .eq(VipOrderDO::getOrderNo, orderNo)
                .eq(VipOrderDO::getUid, VipUserHelper.currentUid())
                .eq(VipOrderDO::getDelFlag, 0)
                .last("limit 1"));
        if (orderDO == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_ORDER_NOT_EXIST);
        }
        return orderDO;
    }

    private VipOrderDO findByRequestId(Long uid, String requestId) {
        if (StrUtil.isBlank(requestId)) {
            return null;
        }
        return vipOrderMapper.selectOne(Wrappers.lambdaQuery(VipOrderDO.class)
                .eq(VipOrderDO::getUid, uid)
                .eq(VipOrderDO::getRequestId, requestId)
                .eq(VipOrderDO::getDelFlag, 0)
                .last("limit 1"));
    }

    /**
     * 同档位 + 同一宠物 + 同优惠码的未超时待支付单（优惠码不同就不复用：用户换了码，应付金额也变了）
     *
     * <p>pid 必须参与：给宠物 A 的待支付单不能拿去复用成宠物 B 的单，否则付完钱会发现
     * 发出去的权益落在另一只宠物身上。
     */
    private VipOrderDO findPendingOrder(Long uid, Long pid, String planCode, Long promoCodeId) {
        LambdaQueryWrapper<VipOrderDO> queryWrapper = Wrappers.lambdaQuery(VipOrderDO.class)
                .eq(VipOrderDO::getUid, uid)
                .eq(VipOrderDO::getPid, VipScopeConstant.normalize(pid))
                .eq(VipOrderDO::getPlanCode, planCode)
                .eq(VipOrderDO::getPayStatus, VipPayStatusEnum.PENDING.getCode())
                .eq(VipOrderDO::getDelFlag, 0);
        if (promoCodeId == null) {
            queryWrapper.isNull(VipOrderDO::getPromoCodeId);
        } else {
            queryWrapper.eq(VipOrderDO::getPromoCodeId, promoCodeId);
        }
        return vipOrderMapper.selectOne(queryWrapper
                .orderByDesc(VipOrderDO::getCreateTime)
                .last("limit 1"));
    }

    /**
     * 定位会员记录：<b>用户 + 宠物 + 档位</b>三要素缺一不可（挂账 #94 的核心修复点）
     *
     * <p>少了 pid 会怎样：同一用户先给宠物 A 买包、再给宠物 B 买，第二次会命中 A 那一行，
     * 把 A 的到期时间往后叠 —— B 什么都没得到、A 白拿一年；退款时同样扣错人。
     *
     * <p>pid 用哨兵值 0 而不是 NULL 比较：NULL 在 MySQL 唯一索引里互不相等，
     * 而且 {@code eq(col, null)} 在 MyBatis-Plus 里根本不是等值条件（见 VipScopeConstant）。
     */
    private UserVipDO findMembership(Long uid, Long pid, String planCode) {
        return userVipMapper.selectOne(Wrappers.lambdaQuery(UserVipDO.class)
                .eq(UserVipDO::getUid, uid)
                .eq(UserVipDO::getPid, VipScopeConstant.normalize(pid))
                .eq(UserVipDO::getPlanCode, planCode)
                .eq(UserVipDO::getDelFlag, 0)
                .last("limit 1"));
    }

    private VipOrderRespDTO toRespDTO(VipOrderDO orderDO, Map<String, Object> payParams) {
        VipOrderRespDTO respDTO = BeanUtil.toBean(orderDO, VipOrderRespDTO.class);
        respDTO.setPayStatusDesc(VipPayStatusEnum.descOf(orderDO.getPayStatus()));
        respDTO.setPayParams(payParams);
        return respDTO;
    }

    private String generateOrderNo() {
        return "VIP" + DateUtil.format(new Date(), "yyyyMMddHHmmss") + RandomUtil.randomNumbers(6);
    }
}