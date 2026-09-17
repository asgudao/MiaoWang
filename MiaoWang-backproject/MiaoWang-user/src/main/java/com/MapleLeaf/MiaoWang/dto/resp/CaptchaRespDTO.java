package com.MapleLeaf.MiaoWang.dto.resp;

import lombok.Data;

/**
 * 图形验证码返回
 */
@Data
public class CaptchaRespDTO {

    /**
     * 验证码标识：校验时要原样回传
     */
    private String captchaKey;

    /**
     * 图片 base64（含 data:image/png;base64, 前缀，前端直接塞给 img.src）
     */
    private String imageBase64;

    /**
     * 有效期（秒）
     */
    private Integer expireSeconds;
}