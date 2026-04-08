package com.aivideo.canvas.service;

import com.aivideo.canvas.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {
    private final AppProperties appProperties;

    public JwtService(AppProperties appProperties) { this.appProperties = appProperties; }

    public String createToken(Long userId){
        SecretKey key = Keys.hmacShaKeyFor(appProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(userId)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(appProperties.getJwtExpireMinutes(), ChronoUnit.MINUTES)))
                .signWith(key).compact();
    }

    public Long parseUserId(String token){
        SecretKey key = Keys.hmacShaKeyFor(appProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        String sub = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
        return Long.parseLong(sub);
    }
}
