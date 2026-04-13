package com.aivideo.canvas.controller;

import com.aivideo.canvas.common.BaseResponse;
import com.aivideo.canvas.service.strategy.KieFactory;
import com.aivideo.canvas.service.strategy.KieStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    @Autowired
    private KieFactory kieFactory;

    @PostMapping("/generate")
    public BaseResponse<String> generateVideo(@RequestBody Map<String, Object> request) {
        String modelName = (String) request.get("modelName");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) request.get("params");

        // 一行代码调用，工厂自动找对策略类
        KieStrategy strategy = kieFactory.getStrategy(modelName);
        String result = strategy.execute(params, "https://你的域名/api/v1/jobs/callback");

        // 【修改这里】：改成调用 .ok()
        return BaseResponse.ok(result);
    }

    @PostMapping("/callback")
    public String kieCallback(@RequestBody Map<String, Object> callbackData) {
        // TODO: 处理 Kie 的回调数据，更新数据库状态，推 WebSocket 通知前端
        System.out.println("收到回调: " + callbackData);
        return "success";
    }

    // 模拟状态存储（实际开发请查数据库或 Redis）
    private Map<String, String> taskStatusMap = new HashMap<>();

    @GetMapping("/status/{taskId}")
    public BaseResponse<Map<String, Object>> getStatus(@PathVariable String taskId) {
        // 这里应该是去调用 Kie API 的查询接口或者查你的数据库
        // 演示逻辑：
        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS"); // 模拟返回成功
        result.put("video_url", "https://www.w3schools.com/html/mov_bbb.mp4");
        return BaseResponse.ok(result);
    }
}