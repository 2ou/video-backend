package com.aivideo.canvas.controller;

import com.aivideo.canvas.common.BaseResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/health")
    public BaseResponse<Map<String,String>> health(){
        String mysql = "down";
        String redis = "down";
        try { jdbcTemplate.queryForObject("SELECT 1", Integer.class); mysql = "up"; } catch (Exception ignored) {}
        try { redisTemplate.getConnectionFactory().getConnection().ping(); redis = "up"; } catch (Exception ignored) {}
        return BaseResponse.ok(Map.of("app","up","mysql",mysql,"redis",redis));
    }
}
