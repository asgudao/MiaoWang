package com.MapleLeaf.MiaoWang.common;


import cn.hutool.jwt.JWT;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT token 生成工具类（基于 Hutool 实现）
 */
@Slf4j
public class JwtTokenUtil {

    // 密钥必须 32 字节以上（HS256 要求 256 bit = 32 字节）
    private static final String SECRET_KEY_STRING = "12345678901234567890123456789012";
    private static final byte[] KEY_BYTES = SECRET_KEY_STRING.getBytes(StandardCharsets.UTF_8);

    /**
     * 生成 JWT token
     *
     * @param claims     载荷数据（会放入 token 的 payload 中）
     * @param subject    主题（会作为 "sub" 字段存入 payload）
     * @param expiration 过期时间（单位：毫秒，从当前时间开始计算）
     * @return JWT 字符串
     */
    public static String generateToken(Map<String, Object> claims, String subject, int expiration) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);
        // 使用 Hutool 的 JWT 构建器
        return JWT.create()
                .setPayload("claims", claims) // 设置自定义载荷
                .setSubject(subject)                // 设置 subject（会覆盖 payload 中的 "sub" 字段）
                .setIssuedAt(now)                   // 签发时间
                .setExpiresAt(expirationDate)       // 过期时间
                .setKey(KEY_BYTES)                  // 设置签名密钥（byte[]）
                .sign();                            // 签名并生成 token 字符串
    }

    /**
     * 解析 JWT 并验证有效性（签名 + 过期时间）
     *
     * @param token JWT 字符串
     * @return 载荷数据（Map 形式）
     * @throws RuntimeException 如果 token 无效、签名错误或已过期
     */
    public static Map<String, Object> parseJwt(String token) {
        // 1. 创建 JWT 对象并设置密钥
        JWT jwt = JWT.of(token).setKey(KEY_BYTES);
        // 2. 验证签名和有效期（过期会返回 false）
        if (!jwt.verify()) {
            throw new RuntimeException("Invalid JWT token or token expired");
        }
        // 3. 获取 payload 数据（Map）
        return jwt.getPayload().getClaimsJson();
    }

    /**
     * 校验JWT token是否有效（签名正确且未过期）
     *
     * @param token JWT字符串
     * @return true-有效，false-无效
     */
    public static boolean isTokenValid(String token) {
        try {
            return JWT.of(token).setKey(KEY_BYTES).verify();
        } catch (Exception e) {
            return false;
        }
    }
}
