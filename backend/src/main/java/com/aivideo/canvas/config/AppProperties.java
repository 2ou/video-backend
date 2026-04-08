package com.aivideo.canvas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private Integer jwtExpireMinutes;
    private String publicBaseUrl;
    private String uploadDir;
}
