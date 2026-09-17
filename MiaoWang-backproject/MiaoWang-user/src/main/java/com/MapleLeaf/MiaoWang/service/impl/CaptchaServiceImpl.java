package com.MapleLeaf.MiaoWang.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.constant.RedisCacheConstant;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.common.enums.UserErrorCodeEnum;
import com.MapleLeaf.MiaoWang.dto.resp.CaptchaRespDTO;
import com.MapleLeaf.MiaoWang.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 图形验证码实现（Hutool 字符验证码 + Redis）
 * <p>
 * 为什么必须有它：短信接口既花钱又能被刷，不加真人校验，脚本可以拿一个手机号把短信额度刷爆。
 * 图形验证码就是短信接口前面的那道闸门。
 * <p>
 * 存 Redis 不存 Session：服务要水平扩容，Session 方案在网关/多实例下还要额外做共享。
 */
@Service
@RequiredArgsConstructor
public class CaptchaServiceImpl implements CaptchaService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 图形验证码有效期（分钟）
     */
    @Value("${captcha.expire-minutes:5}")
    private Integer captchaExpireMinutes;

    @Override
    public CaptchaRespDTO generate() {
        // 4 位字符 + 干扰线；尺寸按手机端展示给
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String captchaKey = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(
                RedisCacheConstant.CAPTCHA_KEY + captchaKey,
                captcha.getCode(),
                captchaExpireMinutes, TimeUnit.MINUTES);
        CaptchaRespDTO respDTO = new CaptchaRespDTO();
        respDTO.setCaptchaKey(captchaKey);
        // 带 data:image/png;base64, 前缀，前端直接 <img :src="imageBase64"> 就能显示
        respDTO.setImageBase64(captcha.getImageBase64Data());
        respDTO.setExpireSeconds(captchaExpireMinutes * 60);
        return respDTO;
    }

    @Override
    public void validate(String captchaKey, String captchaCode) {
        if (StrUtil.isBlank(captchaKey) || StrUtil.isBlank(captchaCode)) {
            throw new ClientException(UserErrorCodeEnum.CAPTCHA_ERROR);
        }
        String redisKey = RedisCacheConstant.CAPTCHA_KEY + captchaKey;
        String rightCode = stringRedisTemplate.opsForValue().get(redisKey);
        // 一次性：先删再判，无论对错都不给第二次机会（防止同一张图被脚本复用刷短信）
        stringRedisTemplate.delete(redisKey);
        if (rightCode == null || !rightCode.equalsIgnoreCase(captchaCode.trim())) {
            throw new ClientException(UserErrorCodeEnum.CAPTCHA_ERROR);
        }
    }
}