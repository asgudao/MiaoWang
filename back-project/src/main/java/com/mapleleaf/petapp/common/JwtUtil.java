package com.mapleleaf.petapp.common;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret:petapp-secret-key-2026}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /** 生成 token */
    public String generate(Long userId, String phone) {
        return JWT.create()
                .withClaim("userId", userId)
                .withClaim("phone", phone)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expiration))
                .sign(Algorithm.HMAC256(secret));
    }

    /** 解析 token */
    public DecodedJWT parse(String token) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret)).build();
        return verifier.verify(token);
    }

    /** 从 token 中提取 userId */
    public Long getUserId(String token) {
        return parse(token).getClaim("userId").asLong();
    }

    /** 验证 token 是否有效 */
    public boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
