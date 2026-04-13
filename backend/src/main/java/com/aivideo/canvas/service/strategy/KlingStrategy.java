package com.aivideo.canvas.service.strategy;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class KlingStrategy implements KieStrategy {
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String getModelPrefix() { return "kling-3.0"; }

    @Override
    public String execute(Map<String, Object> params, String callbackUrl) {
        try {
            // 1. 组装 Kling 专属的 Input 参数
            Map<String, Object> input = new HashMap<>();
            input.put("prompt", params.get("prompt"));
            input.put("image_urls", Collections.singletonList(params.get("image_url")));
            input.put("sound", Boolean.parseBoolean(String.valueOf(params.getOrDefault("sound", "false"))));
            input.put("duration", "5");
            input.put("aspect_ratio", "16:9");
            input.put("mode", "pro");

            // 2. 组装外层请求体
            Map<String, Object> bodyMap = new HashMap<>();
            bodyMap.put("model", "kling-3.0/video"); // 或 motion-control
            bodyMap.put("callBackUrl", callbackUrl);
            bodyMap.put("input", input);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"), mapper.writeValueAsString(bodyMap));

            // 3. 发送 OkHttp 请求 (注意替换你的真实 Token)
            Request request = new Request.Builder()
                    .url("https://api.kie.ai/api/v1/jobs/createTask")
                    .post(body)
                    .addHeader("Authorization", "Bearer <你的真实Token>") 
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    // 这里可以直接用 Jackson 解析返回的 JSON 提取 data.taskId
                    return response.body().string(); 
                }
                throw new RuntimeException("Kie API 调用失败: " + response.code());
            }
        } catch (Exception e) {
            throw new RuntimeException("构建请求异常", e);
        }
    }
}