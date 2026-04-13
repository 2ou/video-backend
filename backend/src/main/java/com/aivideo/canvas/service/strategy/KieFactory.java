package com.aivideo.canvas.service.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KieFactory {
    private final Map<String, KieStrategy> strategyMap = new HashMap<>();

    @Autowired
    public KieFactory(List<KieStrategy> strategies) {
        for (KieStrategy strategy : strategies) {
            strategyMap.put(strategy.getModelPrefix(), strategy);
        }
    }

    public KieStrategy getStrategy(String modelName) {
        return strategyMap.keySet().stream()
                .filter(modelName::startsWith)
                .findFirst()
                .map(strategyMap::get)
                .orElseThrow(() -> new RuntimeException("暂不支持该模型: " + modelName));
    }
}