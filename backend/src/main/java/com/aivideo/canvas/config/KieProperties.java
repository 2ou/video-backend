package com.aivideo.canvas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kie")
public class KieProperties {
    private String baseUrl;
    private String apiKey;
    private String callbackUrl;
}
