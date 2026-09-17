package com.MapleLeaf.MiaoWang.pay;


import com.MapleLeaf.MiaoWang.dao.entity.VipOrderDO;

import java.util.Map;

/**
 * 支付渠道抽象
 *
 * <p>把「下单、验签解析回调、退款、应答」四件事定成接口，业务侧（订单状态机、发会员、回收时长）
 * 完全不用知道用的是微信还是支付宝 —— 接真实渠道时只需新增一个实现类并在 Nacos 配置密钥，
 * 状态机与幂等逻辑一行都不用改。
 *
 * <p>实现类必须是 Spring Bean（{@link PayChannelRegistry} 按 {@link #code()} 注册）。
 */
public interface PayChannel {

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
     * 验签并解析异步回调
     *
     * @param params 回调原始参数（表单或查询串展开后的键值对）
     * @return 解析结果
     * @throws com.MapleLeaf.MiaoWang.common.convention.exception.ClientException 验签失败/时间戳过期
     */
    PayNotifyResult verifyAndParse(Map<String, String> params);

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