package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPayChannelEnum;
import com.MapleLeaf.MiaoWang.pay.PayChannel;
import com.MapleLeaf.MiaoWang.pay.PayChannelRegistry;
import com.MapleLeaf.MiaoWang.service.VipOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 支付渠道异步回调
 *
 * <p><b>必须免登录</b>：微信/支付宝不可能带我们的登录态，所以这条路径要进网关白名单，
 * 安全性完全由渠道自己的验签保证（mock 渠道是 HMAC 验签 + 时间戳 + nonce，见 {@code VipPaySignUtil}；
 * 支付宝是 RSA2 验签，见 {@code AlipayPayChannel}）。
 *
 * <p>安全约束：只接受「允许公网回调」的渠道 —— mock 渠道调这个接口会被拒。
 * 否则知道 mock 密钥的人就能伪造回调，给自己白嫖会员。联调请用
 * {@code POST /vip/order/{orderNo}/pay}（要登录 + 校验订单归属）。
 *
 * <p>应答格式按渠道分派：支付宝要求成功时是纯文本 {@code success}，
 * 套我们的 {@code Result} JSON 信封它认不出来、会按自己的策略重试 24 小时 ——
 * 所以这里按 {@link PayChannel#plainTextAck()} 决定是返回纯文本还是 JSON 信封。
 *
 * <p>接微信时还要注意：微信 v3 的回调用 JSON body + AES-GCM 加密的 resource，
 * 当前的 {@code @RequestParam Map} 只覆盖表单/查询串形式，届时要按渠道改造参数绑定。
 */
@Slf4j
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/pay")
@RequiredArgsConstructor
public class VipPayNotifyController {

    private final VipOrderService vipOrderService;

    private final PayChannelRegistry payChannelRegistry;

    /**
     * 支付结果回调（验签失败/金额不符/状态不允许都会返回错误码，渠道会按自己的策略重试）
     *
     * <p>返回 {@code ResponseEntity} 而不是直接返回 {@code Result}：支付宝那条分支必须由我们
     * 自己决定 Content-Type 是 {@code text/plain}，JSON 分支则原样套 {@code Result} 信封。
     * （方法声明成 {@code Object} 是不行的 —— Spring 只会按<b>声明类型</b>挑返回值处理器，
     * 声明成 Object 时 {@code ResponseEntity} 会被当成普通对象序列化出去。）
     */
    @PostMapping("/notify/{channel}")
    public ResponseEntity<?> notify(@PathVariable("channel") String channel,
                                    @RequestParam Map<String, String> params) {
        VipPayChannelEnum channelEnum = VipPayChannelEnum.of(channel);
        if (channelEnum == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_UNSUPPORTED);
        }
        if (!channelEnum.isExternalNotify()) {
            log.warn("拒绝 {} 渠道的外部回调请求（该渠道不允许公网回调）", channelEnum.getCode());
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_NOTIFY_NOT_ALLOWED);
        }
        PayChannel payChannel = payChannelRegistry.require(channelEnum.getCode());
        String ack = vipOrderService.handleNotify(channelEnum.getCode(), params);
        log.info("处理 {} 渠道支付回调完成，应答 {}", channelEnum.getCode(), ack);
        if (payChannel.plainTextAck()) {
            // 支付宝只认纯文本 success；套 JSON 信封它认不出来，会一直重试
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body(ack);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Results.success(ack));
    }
}