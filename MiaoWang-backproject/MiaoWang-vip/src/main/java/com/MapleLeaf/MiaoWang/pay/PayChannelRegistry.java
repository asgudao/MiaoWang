package com.MapleLeaf.MiaoWang.pay;


import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPayChannelEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付渠道注册表：按编码找到实现，找不到就报错
 *
 * <p><b>刻意不提供"降级到 mock"的兜底</b>：支付渠道解析失败若静默走 mock，
 * 等于用户不花钱就能拿到会员；宁可下单失败，也不能发错会员。
 */
@Component
public class PayChannelRegistry {

    private final Map<String, PayChannel> channelMap;

    public PayChannelRegistry(List<PayChannel> channels) {
        this.channelMap = channels.stream()
                .collect(Collectors.toUnmodifiableMap(channel -> channel.code().toUpperCase(), Function.identity()));
    }

    /**
     * 按编码取渠道实现（不校验是否允许公网回调）
     */
    public PayChannel require(String channelCode) {
        VipPayChannelEnum channelEnum = VipPayChannelEnum.of(channelCode);
        if (channelEnum == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_UNSUPPORTED);
        }
        PayChannel channel = channelMap.get(channelEnum.getCode());
        if (channel == null) {
            // 枚举认得这个渠道，但没有对应实现（比如 WECHAT 还没接入）
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_UNSUPPORTED);
        }
        return channel;
    }

    /**
     * 取「允许公网回调」的渠道：mock 会被拒绝
     */
    public PayChannel requireExternal(String channelCode) {
        VipPayChannelEnum channelEnum = VipPayChannelEnum.of(channelCode);
        if (channelEnum == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_UNSUPPORTED);
        }
        if (!channelEnum.isExternalNotify()) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_NOTIFY_NOT_ALLOWED);
        }
        return require(channelCode);
    }
}