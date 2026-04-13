package com.aivideo.canvas.service.strategy;

import java.util.Map;

public interface KieStrategy {
    // 返回模型前缀，如 "kling-3.0"
    String getModelPrefix();
    // 执行发往 Kie API 的请求
    String execute(Map<String, Object> params, String callbackUrl);
}