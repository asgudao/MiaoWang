package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dto.resp.CaptchaRespDTO;

/**
 * 图形验证码（真人校验）
 */
public interface CaptchaService {

    /**
     * 生成图形验证码
     *
     * @return captchaKey（前端回传用）+ 图片 base64
     */
    CaptchaRespDTO generate();

    /**
     * 校验图形验证码：校验后即作废（一次性），防止同一张图被脚本反复复用
     *
     * @param captchaKey  生成时返回的 key
     * @param captchaCode 用户输入的验证码
     */
    void validate(String captchaKey, String captchaCode);
}