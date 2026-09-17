package com.MapleLeaf.MiaoWang.service.impl;

import com.MapleLeaf.MiaoWang.enums.SmsSceneEnum;
import com.MapleLeaf.MiaoWang.service.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 短信发送的 Mock 实现（开发/演示用）
 * <p>
 * 不真发短信：个人开发者接云厂商短信需要企业资质 + 已审核的签名模板，成本高且阻塞开发。
 * 这里把验证码打进日志，联调时从日志里取码即可。
 * <p>
 * {@code @ConditionalOnProperty}：配了 {@code sms.provider=aliyun} 之类时本 Bean 不生效，
 * 由真实实现接管（默认 matchIfMissing=true，即不配就是 mock）。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsSender implements SmsSender {

    @Override
    public void send(String phone, String code, SmsSceneEnum scene) {
        log.info("[MOCK 短信] 场景={}({}) 手机号={} 验证码={} —— 未真实发送，联调请从本行日志取码",
                scene.getDesc(), scene.getScene(), phone, code);
    }
}