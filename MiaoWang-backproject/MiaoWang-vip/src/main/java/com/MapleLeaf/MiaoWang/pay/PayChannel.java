package com.MapleLeaf.MiaoWang.pay;


import com.MapleLeaf.MiaoWang.dao.entity.VipOrderDO;

import java.util.Map;

/**
 * 支付渠道抽象
 *
 * <p>把「下单、验签解析回调、退款、应答」四件事定成接口，另外用默认方法补了可选的「主动查单」，
 * 业务侧（订单状态机、发会员、回收时长）完全不用知道用的是微信还是支付宝 —— 接真实渠道时只需新增一个
 * 实现类并在 Nacos 配置密钥，状态机与幂等逻辑一行都不用改。
 *
 * <p>实现类必须是 Spring Bean（{@link PayChannelRegistry} 按 {@link #code()} 注册）。
 */
public interface PayChannel {

    /**
     * 支付场景：网页/收银台跳转支付（<b>各渠道的默认场景</b>）
     *
     * <p>用户被跳去渠道自己的页面完成支付（支付宝就是 {@code alipay.trade.page.pay}）。
     */
    String SCENE_PAGE = "PAGE";

    /**
     * 支付场景：扫码支付
     *
     * <p>用户<b>不离开我们的页面</b>：渠道返回一串支付码，我们把它渲染成二维码，
     * 用户用手机上的渠道 App 扫码付款（支付宝就是 {@code alipay.trade.precreate}）。
     */
    String SCENE_QR = "QR";

    /**
     * 渠道编码，取值与 {@code VipPayChannelEnum.code} 一致
     */
    String code();

    /**
     * 下单：返回给前端的支付参数（二维码链接、预支付ID、调起参数等）
     *
     * @param orderDO 待支付订单
     */
    Map<String, Object> createPayment(VipOrderDO orderDO);

    /**
     * 按<b>支付场景</b>下单（可选能力）
     *
     * <p>{@code scene} 取值见 {@link #SCENE_PAGE} / {@link #SCENE_QR}；<b>空值 = 渠道默认场景</b>
     * （即 {@link #SCENE_PAGE}）。比起"再加一个方法名"，用 scene 参数的好处是：
     * 同一个渠道的多种收款形态（跳转 / 扫码 / 小程序）都由"场景"这一个维度表达，
     * 订单状态机、回调、退款、对账完全不用知道用户是怎么付的钱。
     *
     * <p>默认实现<b>原样委托</b>给 {@link #createPayment(VipOrderDO)}：mock 与其它尚未实现扫码的渠道
     * 一行都不用改，传什么 scene 都还是它们原来的行为。
     *
     * @param orderDO 待支付订单
     * @param scene   支付场景（{@code QR} / {@code PAGE} / 空）
     * @throws com.MapleLeaf.MiaoWang.common.convention.exception.ClientException 场景不被该渠道支持
     */
    default Map<String, Object> createPayment(VipOrderDO orderDO, String scene) {
        return createPayment(orderDO);
    }

    /**
     * 验签并解析异步回调
     *
     * @param params 回调原始参数（表单或查询串展开后的键值对）
     * @return 解析结果
     * @throws com.MapleLeaf.MiaoWang.common.convention.exception.ClientException 验签失败/时间戳过期
     */
    PayNotifyResult verifyAndParse(Map<String, String> params);

    /**
     * 主动查单（对账）
     *
     * <p><b>为什么必须有</b>：异步回调是会丢的 —— 公网抖动、渠道重试次数耗尽、开发机在局域网内
     * 压根收不到回调。真实支付里同样必须能自愈，否则就是"用户付了钱、我们这边还是待支付"。
     * 所以查单不是联调权宜之计，是支付链路的正常组成部分。
     *
     * <p>默认返回 {@code null}（= 本渠道不支持查单），这样已有的 mock 渠道一行都不用改；
     * 调用方（{@code VipOrderService#syncOrder}）拿到 null 就只回读当前订单状态，不猜。
     *
     * @param outTradeNo 商户订单号
     * @return 查单结果；不支持查单的渠道返回 {@code null}
     * @throws com.MapleLeaf.MiaoWang.common.convention.exception.ClientException 渠道调用失败
     */
    default PayQueryResult queryTrade(String outTradeNo) {
        return null;
    }

    /**
     * 异步回调是否必须通过「nonce 一次性」校验（默认 true）
     *
     * <p>mock 渠道用 HMAC 自签，签名串本身可以被无限重放，所以必须靠 nonce 兜底。
     * 支付宝的异步通知是它自己的 RSA2 签名体系、<b>没有 nonce 字段</b>：它的重放防护是
     * 「订单已是已支付就直接幂等应答」+ 状态机 CAS，重复通知是无害的。
     *
     * <p><b>返回 false 的实现类必须自己保证"重复通知不会重复发货"</b>，否则等于开了个白嫖口子。
     */
    default boolean notifyNonceRequired() {
        return true;
    }

    /**
     * 成功应答是否必须是纯文本（默认 false，走业务的 {@code Result} JSON 信封）
     *
     * <p>支付宝要求处理成功时回纯文本 {@code success}：套 JSON 信封它认不出来，
     * 会当成处理失败并按自己的策略重试 24 小时。
     */
    default boolean plainTextAck() {
        return false;
    }

    /**
     * 退款
     *
     * @param orderDO 已支付订单
     * @param reason  退款原因（可为空）
     */
    PayRefundResult refund(VipOrderDO orderDO, String reason);

    /**
     * 处理成功时应答给渠道的内容（微信要求返回 "SUCCESS" 纯文本）
     */
    String ackBody();
}