package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPayChannelEnum;
import com.MapleLeaf.MiaoWang.service.VipOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付渠道异步回调
 *
 * <p><b>必须免登录</b>：微信/支付宝不可能带我们的登录态，所以这条路径要进网关白名单，
 * 安全性完全由「HMAC 验签 + 时间戳新鲜度 + nonce 一次性」保证（见 {@code VipPaySignUtil}）。
 *
 * <p>安全约束：只接受「允许公网回调」的渠道 —— mock 渠道调这个接口会被拒。
 * 否则知道 mock 密钥的人就能伪造回调，给自己白嫖会员。联调请用
 * {@code POST /vip/order/{orderNo}/pay}（要登录 + 校验订单归属）。
 *
 * <p>接真实渠道时注意两点：
 * <ul>
 *   <li>支付宝 v3 用 JSON body、微信 v3 用 AES-GCM 加密的 resource，
 *       当前的 {@code @RequestParam Map} 只覆盖表单/查询串形式，届时要按渠道改造参数绑定</li>
 *   <li>渠道要求应答格式不同（微信要纯文本 {@code SUCCESS}），届时这里要直接输出纯文本、
 *       不套 {@code Result} 信封</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/mapleleaf/miaowang/v1/vip/pay")
@RequiredArgsConstructor
public class VipPayNotifyController {

    private final VipOrderService vipOrderService;

    /**
     * 支付结果回调（验签失败/金额不符/状态不允许都会返回错误码，渠道会按自己的策略重试）
     */
    @PostMapping("/notify/{channel}")
    public Result<String> notify(@PathVariable("channel") String channel,
                                 @RequestParam Map<String, String> params) {
        VipPayChannelEnum channelEnum = VipPayChannelEnum.of(channel);
        if (channelEnum == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_UNSUPPORTED);
        }
        if (!channelEnum.isExternalNotify()) {
            log.warn("拒绝 {} 渠道的外部回调请求（该渠道不允许公网回调）", channelEnum.getCode());
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_NOTIFY_NOT_ALLOWED);
        }
        String ack = vipOrderService.handleNotify(channelEnum.getCode(), params);
        log.info("处理 {} 渠道支付回调完成，应答 {}", channelEnum.getCode(), ack);
        return Results.success(ack);
    }
}