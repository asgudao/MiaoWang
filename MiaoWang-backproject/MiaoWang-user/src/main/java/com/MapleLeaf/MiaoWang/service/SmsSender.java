package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.enums.SmsSceneEnum;

/**
 * 短信发送（可插拔）
 * <p>
 * 现在只有 {@link com.MapleLeaf.MiaoWang.service.impl.MockSmsSender} 一个实现（打日志，不真发），
 * 以后接阿里云/腾讯云时新增一个实现类并把配置 {@code sms.provider} 改掉即可，
 * 业务代码（限流、存码、校验）一行都不用动。
 */
public interface SmsSender {

    /**
     * 发送短信验证码
     *
     * @param phone 手机号
     * @param code  验证码
     * @param scene 使用场景（不同场景文案不同）
     */
    void send(String phone, String code, SmsSceneEnum scene);
}