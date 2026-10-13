package com.example.backend.security;

import com.example.backend.common.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-hours}")
    private long expireHours;

    @Value("${app.jwt.issuer}")
    private String issuer;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 token：payload 含 userId / role / 过期时间 */
    public String generate(LoginUser user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();
    }

    /** 解析并校验 token，非法/过期返回 null */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class));
        } catch (Exception e) {
            return null;
        }
    }
}
