package com.MapleLeaf.MiaoWang.service;


import com.MapleLeaf.MiaoWang.dto.req.VipOrderCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipPromoGrantReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.VipRefundReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.VipOrderRespDTO;

import java.util.List;
import java.util.Map;

/**
 * 会员订单（支付状态机）
 *
 * <p>下单与发货分离：{@link #createOrder} 只落「待支付」订单，<b>不发会员</b>；
 * 只有支付回调（{@link #handleNotify}）把订单 CAS 改成「已支付」的那一次，才在同一事务里发放会员。
 */
public interface VipOrderService {

    /**
     * 创建待支付订单（幂等：同一 requestId / 同一档位的未超时待支付单会复用）
     */
    VipOrderRespDTO createOrder(VipOrderCreateReqDTO requestParam);

    /**
     * 模拟支付（联调用）：造一条带签名的回调，走和真实回调完全相同的链路
     */
    VipOrderRespDTO mockPay(String orderNo);

    /**
     * 处理支付渠道回调：验签 → 时间戳 → nonce 防重放 → 金额校验 → CAS 状态流转 → 事务内发会员
     *
     * @param channelCode 渠道编码
     * @param params      回调原始参数
     * @return 应答给渠道的内容
     */
    String handleNotify(String channelCode, Map<String, String> params);

    /**
     * 主动查单对账：向渠道问这笔单，渠道说付了就补走回调那条链路发货
     *
     * <p><b>为什么需要</b>：异步回调会丢 —— 公网抖动、渠道重试次数耗尽、开发机在局域网内
     * 压根收不到回调。「渠道说付了、我们这边还是待支付」必须能自愈，否则用户付了钱拿不到会员；
     * 真实支付里也必须能主动对账，所以这不是联调权宜之计。
     *
     * <p>仅订单本人可调（越权按"订单不存在"处理）；幂等：订单已不是待支付就直接回读当前状态，
     * 渠道说没付也只是回读，都不会重复发会员。
     */
    VipOrderRespDTO syncOrder(String orderNo);

    /**
     * 订单退款（仅本人、仅已支付、仅窗口期内；幂等）
     */
    VipOrderRespDTO refund(String orderNo, VipRefundReqDTO requestParam);

    /**
     * 我的订单（顺手把超时的待支付单关掉）
     */
    List<VipOrderRespDTO> listMyOrders();

    /**
     * 运营：订单查询（可按支付状态 / 订单号过滤，后台用，不关单不写库）
     */
    List<VipOrderRespDTO> listOrdersForAdmin(Integer payStatus, String orderNo, int limit);

    /**
     * 促销单：生成一张 0 元「已支付」订单并发放会员时长（由促销服务在核销后调用）
     *
     * <p>为什么不直接在促销服务里写会员表：发会员的口径（同档位叠加、时长快照、订单回写）
     * 必须只有一处实现，否则两条路径迟早会算出不一样的到期时间。
     */
    VipOrderRespDTO createPromoOrder(VipPromoGrantReqDTO requestParam);

    /**
     * 运营代退：不受「7 天自助退款窗口」与订单归属限制（后台按订单号操作，无归属概念）
     *
     * <p>必须有：自助窗口是给用户的，运营侧遇到投诉/渠道异常需要能兜底退款。
     * 状态机与幂等（CAS + 时长回收）与自助退款完全共用一套逻辑。
     */
    VipOrderRespDTO adminRefund(String orderNo, String reason);
}