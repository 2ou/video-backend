package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.config.KieProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service @Slf4j
public class KieService {
    private final KieProperties kieProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    public KieService(KieProperties kieProperties) { this.kieProperties = kieProperties; }

    public String submitVideoTask(Map<String, Object> payload){
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(kieProperties.getBaseUrl()+"/v1/video/tasks", HttpMethod.POST, new HttpEntity<>(payload, headers()), Map.class);
            Map<?,?> body = resp.getBody();
            Object id = body == null ? null : (body.get("task_id") != null ? body.get("task_id") : ((Map<?,?>)body.getOrDefault("data", Map.of())).get("task_id"));
            if (id == null) throw new AppException("KIE_BAD_RESPONSE","KIE submit response missing task_id");
            return String.valueOf(id);
        } catch (Exception e){
            log.error("KIE submit failed: {}", e.getMessage());
            throw new AppException("KIE_SUBMIT_FAILED","KIE submit failed");
        }
    }

    public Map<String,Object> queryTask(String providerTaskId){
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(kieProperties.getBaseUrl()+"/v1/video/tasks/"+providerTaskId, HttpMethod.GET, new HttpEntity<>(headers()), Map.class);
            return resp.getBody();
        } catch (Exception e){
            throw new AppException("KIE_QUERY_FAILED","KIE query failed");
        }
    }

    public String normalizeStatus(String rawStatus){
        String s = rawStatus == null ? "" : rawStatus.toLowerCase();
        if (List.of("queued","pending","waiting").contains(s)) return "queued";
        if (List.of("running","processing").contains(s)) return "running";
        if (List.of("success","succeeded","done","completed").contains(s)) return "success";
        if (List.of("failed","error","canceled").contains(s)) return "failed";
        return "running";
    }

    public List<String> extractResultUrls(Map<String,Object> raw){
        Map<String,Object> data = raw.get("data") instanceof Map<?,?> m ? (Map<String,Object>)m : raw;
        List<String> urls = new ArrayList<>();
        Object arr = data.get("result_urls");
        if (arr instanceof List<?> l) l.forEach(it -> urls.add(String.valueOf(it)));
        if (data.get("result_url") != null) urls.add(String.valueOf(data.get("result_url")));
        return urls;
    }

    private HttpHeaders headers(){
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(kieProperties.getApiKey());
        h.set("Content-Type", "application/json");
        return h;
    }
}
