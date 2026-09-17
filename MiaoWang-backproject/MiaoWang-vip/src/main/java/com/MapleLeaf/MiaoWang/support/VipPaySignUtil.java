package com.MapleLeaf.MiaoWang.support;


import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * 支付回调验签工具（HMAC-SHA256）
 *
 * <p>规则：把回调参数按 key 字典序拼成 {@code k=v&k=v}（<b>排除 {@code sign} 本身与空值</b>），
 * 用渠道密钥做 HMAC-SHA256，十六进制小写就是签名。
 *
 * <p>三个容易被忽略的点，都实现了：
 * <ul>
 *   <li><b>常量时间比较</b>：用 {@link MessageDigest#isEqual} 而不是 {@code equals}，
 *       避免"逐字节比较"被时序攻击试出签名</li>
 *   <li><b>时间戳新鲜度</b>：光验签不够 —— 一条合法回调被录下来以后可以反复重放，
 *       所以还要限制时间戳偏移（默认 5 分钟）</li>
 *   <li><b>nonce 一次性</b>：由调用方配 Redis 做（见 VipOrderServiceImpl），这里只做纯计算，
 *       保证本类无状态、可单测</li>
 * </ul>
 */
public final class VipPaySignUtil {

    /**
     * 签名字段名
     */
    public static final String SIGN_FIELD = "sign";

    /**
     * 时间戳字段名（秒级）
     */
    public static final String TIMESTAMP_FIELD = "timestamp";

    /**
     * 随机串字段名（防重放用）
     */
    public static final String NONCE_FIELD = "nonce";

    private static final String HMAC_SHA256 = "HmacSHA256";

    private VipPaySignUtil() {
    }

    /**
     * 拼接待签名串：按 key 字典序，跳过 {@code sign} 与空值
     */
    public static String buildSignContent(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (SIGN_FIELD.equals(entry.getKey()) || StrUtil.isBlank(entry.getValue())) {
                continue;
            }
            if (!content.isEmpty()) {
                content.append("&");
            }
            content.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return content.toString();
    }

    /**
     * 计算签名
     */
    public static String sign(Map<String, String> params, String secret) {
        return hmacSha256(buildSignContent(params), secret);
    }

    /**
     * 验签 + 时间戳新鲜度校验，不通过直接抛异常（<b>绝不返回 boolean 让调用方"忘了判断"</b>）
     *
     * @param params      回调参数（含 sign / timestamp）
     * @param secret      渠道密钥
     * @param skewSeconds 允许的时间戳偏移秒数
     */
    public static void verify(Map<String, String> params, String secret, int skewSeconds) {
        if (params == null || params.isEmpty()) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        String sign = params.get(SIGN_FIELD);
        if (StrUtil.isBlank(sign)) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        String expect = sign(params, secret);
        // 常量时间比较：逐字节比较会被计时攻击一点一点试出正确签名
        if (!MessageDigest.isEqual(expect.getBytes(StandardCharsets.UTF_8), sign.getBytes(StandardCharsets.UTF_8))) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        long timestamp = parseTimestamp(params.get(TIMESTAMP_FIELD));
        if (timestamp <= 0) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        long skew = Math.abs(System.currentTimeMillis() / 1000 - timestamp);
        if (skew > skewSeconds) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_REPLAY);
        }
    }

    /**
     * 解析秒级时间戳，非法返回 -1
     */
    private static long parseTimestamp(String value) {
        if (StrUtil.isBlank(value)) {
            return -1L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static String hmacSha256(String content, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexUtil.encodeHexStr(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            // 算法/密钥异常属于环境问题（JDK 不支持 HmacSHA256 才会发生），交给全局异常处理成系统错误
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }
}