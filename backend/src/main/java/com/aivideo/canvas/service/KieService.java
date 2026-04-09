package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.config.KieProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class KieService {
    private static final Set<String> QUEUED_STATES = Set.of("queued", "queueing", "pending", "waiting", "submitted");
    private static final Set<String> RUNNING_STATES = Set.of("running", "processing", "generating", "in_progress");
    private static final Set<String> SUCCESS_STATES = Set.of("success", "succeeded", "done", "completed", "finish");
    private static final Set<String> FAILED_STATES = Set.of("failed", "error", "canceled", "cancelled", "timeout");

    private static final String GPT_PATH = "gpt-5-2";
    private static final String CLAUDE_PATH = "claude-sonnet-4-5";

    private final KieProperties kieProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KieService(KieProperties kieProperties) {
        this.kieProperties = kieProperties;
    }

    public String submitVideoTask(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        String model = asString(safePayload.get("model"), "grok-imagine/text-to-video");
        Map<String, Object> input = new LinkedHashMap<>();

        Object inputObj = safePayload.get("input");
        if (inputObj instanceof Map<?, ?> mapInput) {
            mapInput.forEach((k, v) -> input.put(String.valueOf(k), v));
        }

        if (input.isEmpty()) {
            Object paramsObj = safePayload.get("params");
            if (paramsObj instanceof Map<?, ?> mapParams) {
                mapParams.forEach((k, v) -> input.put(String.valueOf(k), v));
            }
            mergeIfPresent(input, "prompt", safePayload.get("prompt"));
            mergeIfPresent(input, "negative_prompt", safePayload.get("negative_prompt"));
            mergeIfPresent(input, "image_urls", safePayload.get("image_urls"));
            mergeIfPresent(input, "image_url", safePayload.get("image_url"));
        }

        return submitTask(model, input);
    }

    public String submitTask(String model, Map<String, Object> input) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("input", input == null ? Map.of() : input);
            if (!isBlank(kieProperties.getCallbackUrl())) {
                payload.put("callBackUrl", kieProperties.getCallbackUrl());
            }

            Map<String, Object> body = postJson("/api/v1/jobs/createTask", payload);
            Object taskId = firstNonNull(
                    dig(body, "data", "taskId"),
                    dig(body, "data", "task_id"),
                    body.get("taskId"),
                    body.get("task_id")
            );
            if (taskId == null || isBlank(String.valueOf(taskId))) {
                throw new AppException("KIE_BAD_RESPONSE", "KIE submit response missing taskId");
            }
            return String.valueOf(taskId);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("KIE submit failed: {}", e.getMessage(), e);
            throw new AppException("KIE_SUBMIT_FAILED", "KIE submit failed");
        }
    }

    public Map<String, Object> queryTask(String providerTaskId) {
        try {
            String encodedTaskId = URLEncoder.encode(providerTaskId, StandardCharsets.UTF_8);
            return getJson("/api/v1/jobs/recordInfo?taskId=" + encodedTaskId);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("KIE query failed: {}", e.getMessage(), e);
            throw new AppException("KIE_QUERY_FAILED", "KIE query failed");
        }
    }

    public String resolveTaskStatus(Map<String, Object> raw) {
        String rawStatus = firstNonBlank(
                asString(raw.get("state"), ""),
                asString(raw.get("status"), ""),
                asString(dig(raw, "data", "state"), ""),
                asString(dig(raw, "data", "status"), "")
        );
        return normalizeStatus(rawStatus);
    }

    public String normalizeStatus(String rawStatus) {
        String s = rawStatus == null ? "" : rawStatus.trim().toLowerCase();
        if (QUEUED_STATES.contains(s)) return "queued";
        if (RUNNING_STATES.contains(s)) return "running";
        if (SUCCESS_STATES.contains(s)) return "success";
        if (FAILED_STATES.contains(s)) return "failed";
        return "running";
    }

    public List<String> extractResultUrls(Map<String, Object> raw) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        collectUrls(urls, raw);

        Map<String, Object> data = asMap(raw.get("data"));
        collectUrls(urls, data);

        Object resultJson = data.get("resultJson");
        Map<String, Object> resultJsonMap = parseJsonMap(resultJson);
        collectUrls(urls, resultJsonMap);
        collectUrls(urls, resultJsonMap.get("response"));

        return new ArrayList<>(urls);
    }

    public String reversePromptFromImage(String imageUrl, String hint) {
        String task = "Write one concise, high-quality English video generation prompt based on this image.";
        if (!isBlank(hint)) {
            task += " Additional style hint: " + hint.trim();
        }

        List<Map<String, Object>> imageContent = List.of(
                Map.of("type", "text", "text", task),
                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
        );
        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", imageContent));

        for (String path : List.of(GPT_PATH, CLAUDE_PATH)) {
            try {
                String prompt = chatCompletion(path, messages);
                if (!isBlank(prompt)) {
                    return prompt.trim();
                }
            } catch (Exception e) {
                log.warn("KIE reverse prompt by {} failed: {}", path, e.getMessage());
            }
        }

        return "[fallback] " + buildFallbackPrompt(imageUrl, hint);
    }

    public String polishPrompt(String text, String model, String styleHint) {
        if (isBlank(text)) {
            throw new AppException("VALIDATION_ERROR", "text is required");
        }

        String modelPath = resolveTextModel(model);
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Improve this video-generation prompt. Keep core intent and output only the improved prompt:\n")
                .append(text.trim());
        if (!isBlank(styleHint)) {
            userPrompt.append("\nStyle hint: ").append(styleHint.trim());
        }

        List<Map<String, Object>> messages = List.of(
                Map.of(
                        "role", "system",
                        "content", "You are an expert prompt engineer for cinematic AI video generation. Return only prompt text."
                ),
                Map.of("role", "user", "content", userPrompt.toString())
        );

        return chatCompletion(modelPath, messages).trim();
    }

    public String resolveTextModel(String model) {
        String normalized = asString(model, GPT_PATH).trim().toLowerCase();
        if (normalized.equals("gpt") || normalized.equals(GPT_PATH)) return GPT_PATH;
        if (normalized.equals("claude") || normalized.equals(CLAUDE_PATH)) return CLAUDE_PATH;
        throw new AppException("VALIDATION_ERROR", "unsupported text model: " + model);
    }

    private String chatCompletion(String modelPath, List<Map<String, Object>> messages) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", messages);
        payload.put("max_tokens", 700);

        Map<String, Object> body = postJson("/" + modelPath + "/v1/chat/completions", payload);
        String text = extractChatText(body);
        if (isBlank(text)) {
            throw new AppException("KIE_BAD_RESPONSE", "KIE text response missing content");
        }
        return text;
    }

    private Map<String, Object> postJson(String path, Map<String, Object> payload) {
        ResponseEntity<Map> resp = restTemplate.exchange(
                kieProperties.getBaseUrl() + path,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers()),
                Map.class
        );
        return sanitizeBody(resp.getBody());
    }

    private Map<String, Object> getJson(String path) {
        ResponseEntity<Map> resp = restTemplate.exchange(
                kieProperties.getBaseUrl() + path,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                Map.class
        );
        return sanitizeBody(resp.getBody());
    }

    private Map<String, Object> sanitizeBody(Map<?, ?> body) {
        if (body == null) return Map.of();

        Map<String, Object> copied = new LinkedHashMap<>();
        body.forEach((k, v) -> copied.put(String.valueOf(k), v));

        Object code = copied.get("code");
        if (code instanceof Number number && number.intValue() != 200) {
            throw new AppException("KIE_BAD_RESPONSE", asString(copied.get("msg"), "KIE request failed"));
        }
        return copied;
    }

    private void collectUrls(LinkedHashSet<String> urls, Object value) {
        if (value == null) return;

        if (value instanceof String text) {
            String candidate = text.trim();
            if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                urls.add(candidate);
                return;
            }
            if ((candidate.startsWith("{") && candidate.endsWith("}")) || (candidate.startsWith("[") && candidate.endsWith("]"))) {
                collectUrls(urls, parseJsonMap(candidate));
            }
            return;
        }

        if (value instanceof List<?> list) {
            list.forEach(item -> collectUrls(urls, item));
            return;
        }

        if (!(value instanceof Map<?, ?> mapValue)) return;

        Map<String, Object> map = new LinkedHashMap<>();
        mapValue.forEach((k, v) -> map.put(String.valueOf(k), v));

        for (String key : List.of(
                "resultUrls", "result_urls", "videoUrls", "video_urls", "imageUrls", "image_urls", "output", "outputs"
        )) {
            collectUrls(urls, map.get(key));
        }
        for (String key : List.of("url", "resultUrl", "result_url", "video_url", "image_url")) {
            Object item = map.get(key);
            if (item != null) {
                String candidate = String.valueOf(item).trim();
                if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                    urls.add(candidate);
                }
            }
        }
    }

    private Map<String, Object> parseJsonMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> copied = new LinkedHashMap<>();
            mapValue.forEach((k, v) -> copied.put(String.valueOf(k), v));
            return copied;
        }
        if (!(value instanceof String text) || text.isBlank()) return Map.of();

        try {
            return objectMapper.readValue(text, new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String extractChatText(Map<String, Object> body) {
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) return null;

        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> firstChoice)) return null;

        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            return asString(firstChoice.get("text"), null);
        }

        Object contentObj = message.get("content");
        if (contentObj instanceof String contentText) {
            return contentText;
        }
        if (!(contentObj instanceof List<?> contentList) || contentList.isEmpty()) return null;

        StringBuilder text = new StringBuilder();
        for (Object part : contentList) {
            if (part instanceof String partText) {
                text.append(partText);
                continue;
            }
            if (!(part instanceof Map<?, ?> partMap)) continue;
            Object candidate = partMap.get("text");
            if (candidate != null) {
                text.append(candidate);
            }
        }
        return text.toString();
    }

    private Object dig(Map<String, Object> source, String parentKey, String childKey) {
        if (source == null) return null;
        Object parent = source.get(parentKey);
        if (!(parent instanceof Map<?, ?> map)) return null;
        return map.get(childKey);
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> mapValue)) return Map.of();
        Map<String, Object> copied = new LinkedHashMap<>();
        mapValue.forEach((k, v) -> copied.put(String.valueOf(k), v));
        return copied;
    }

    private void mergeIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) return;
        if (value instanceof String text && text.isBlank()) return;
        target.put(key, value);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return "";
    }

    private String asString(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String buildFallbackPrompt(String imageUrl, String hint) {
        String imageName = Optional.ofNullable(imageUrl)
                .map(url -> {
                    try {
                        return URI.create(url).getPath();
                    } catch (Exception ignored) {
                        return url;
                    }
                })
                .map(path -> {
                    if (path == null || path.isBlank()) return "reference-image";
                    int idx = path.lastIndexOf('/');
                    return idx >= 0 ? path.substring(idx + 1) : path;
                })
                .filter(name -> !name.isBlank())
                .orElse("reference-image");

        String base = "cinematic scene, clear subject, rich detail, natural lighting, smooth motion cues";
        if (!isBlank(hint)) {
            base += ", style hint: " + hint.trim();
        }
        return base + ", based on " + imageName;
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(kieProperties.getApiKey());
        h.set("Content-Type", "application/json");
        return h;
    }
}
