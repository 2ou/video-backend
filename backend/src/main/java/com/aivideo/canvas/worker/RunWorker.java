package com.aivideo.canvas.worker;

import com.aivideo.canvas.entity.Asset;
import com.aivideo.canvas.entity.ProjectVersion;
import com.aivideo.canvas.entity.WorkflowRun;
import com.aivideo.canvas.entity.WorkflowRunNode;
import com.aivideo.canvas.repository.AssetRepository;
import com.aivideo.canvas.repository.ProjectVersionRepository;
import com.aivideo.canvas.repository.WorkflowRunNodeRepository;
import com.aivideo.canvas.repository.WorkflowRunRepository;
import com.aivideo.canvas.service.KieService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RunWorker {
    private final WorkflowRunRepository runRepository;
    private final WorkflowRunNodeRepository nodeRepository;
    private final ProjectVersionRepository versionRepository;
    private final AssetRepository assetRepository;
    private final KieService kieService;
    private final PollingWorker pollingWorker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RunWorker(
            WorkflowRunRepository runRepository,
            WorkflowRunNodeRepository nodeRepository,
            ProjectVersionRepository versionRepository,
            AssetRepository assetRepository,
            KieService kieService,
            PollingWorker pollingWorker
    ) {
        this.runRepository = runRepository;
        this.nodeRepository = nodeRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.kieService = kieService;
        this.pollingWorker = pollingWorker;
    }

    @Async
    public void execute(Long runId) {
        WorkflowRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;

        ProjectVersion version = versionRepository.findById(run.getVersionId()).orElse(null);
        if (version == null) return;

        List<WorkflowRunNode> runNodes = nodeRepository.findByRunIdOrderByIdAsc(runId);

        try {
            Map<String, Object> canvas = parseJsonMap(version.getCanvasJson());
            Map<String, Object> promptNodeData = findNodeData(canvas, "prompt_input");
            Map<String, Object> imageNodeData = findNodeData(canvas, "input_video");

            String legacyPrompt = resolvePrompt(promptNodeData);
            String imageMode = asString(imageNodeData.get("mode"), "");
            String imageToImagePrompt = asString(imageNodeData.get("image_to_image_prompt"), "");
            if ("image_to_image".equals(imageMode) && !imageToImagePrompt.isBlank()) {
                legacyPrompt = legacyPrompt.isBlank() ? imageToImagePrompt : legacyPrompt + ", " + imageToImagePrompt;
            }
            Object legacyInputAssetId = imageNodeData.get("asset_id");

            boolean hasAsyncTask = false;

            for (WorkflowRunNode node : runNodes) {
                String nodeType = asString(node.getNodeType(), "");

                if ("video_gen".equals(nodeType)) {
                    submitAtomicVideoGenTask(node, run.getUserId());
                    hasAsyncTask = true;
                    continue;
                }

                // 旧节点兼容：保留 kie_video_task 执行逻辑
                if ("kie_video_task".equals(nodeType)) {
                    submitLegacyKieTask(node, run.getUserId(), legacyPrompt, legacyInputAssetId);
                    hasAsyncTask = true;
                    continue;
                }

                node.setStatus("success");
                nodeRepository.save(node);
            }

            if (hasAsyncTask) {
                run.setStatus("running");
            } else {
                run.setStatus("success");
                run.setFinishedAt(LocalDateTime.now());
            }
            runRepository.save(run);
        } catch (Exception e) {
            run.setStatus("failed");
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);
        }
    }

    /**
     * 新架构核心逻辑：
     * VideoGenNode 的 node.data 在前端运行前已被图编译器注入 input_payload。
     * 后端这里只需要读取 input_payload 并转成最终 KIE payload。
     */
    private void submitAtomicVideoGenTask(WorkflowRunNode node, Long userId) {
        Map<String, Object> nodeInput = readJsonMap(node.getInputJson());
        Map<String, Object> inputPayload = toMutableMap(nodeInput.get("input_payload"));

        String model = asString(firstNonNull(inputPayload.get("model"), nodeInput.get("model")), "seedance/2.0-real");
        Map<String, Object> finalInput = buildAtomicVideoInput(userId, nodeInput, inputPayload);

        String taskId = kieService.submitTask(model, finalInput);
        node.setProvider("kie");
        node.setProviderTaskId(taskId);
        node.setStatus("running");
        node.setStartedAt(LocalDateTime.now());
        nodeRepository.save(node);
        pollingWorker.schedule(node.getId());
    }

    private Map<String, Object> buildAtomicVideoInput(
            Long userId,
            Map<String, Object> nodeInput,
            Map<String, Object> inputPayload
    ) {
        Map<String, Object> input = new LinkedHashMap<>();

        // 透传基础参数（优先 input_payload，再回退 node 顶层字段）
        copyPreferred(inputPayload, nodeInput, input, "prompt");
        copyPreferred(inputPayload, nodeInput, input, "mode");
        copyPreferred(inputPayload, nodeInput, input, "resolution");
        copyPreferred(inputPayload, nodeInput, input, "aspect_ratio");
        copyPreferred(inputPayload, nodeInput, input, "duration");
        copyPreferred(inputPayload, nodeInput, input, "fps");
        copyPreferred(inputPayload, nodeInput, input, "negative_prompt");
        copyPreferred(inputPayload, nodeInput, input, "audio_sync");

        // 1) first_frame_asset_id -> first_frame_url / image_url
        Object firstFrameAssetId = firstNonNull(inputPayload.get("first_frame_asset_id"), nodeInput.get("first_frame_asset_id"));
        String firstFrameUrl = resolveAssetUrl(firstFrameAssetId, userId);
        if (firstFrameUrl != null && !firstFrameUrl.isBlank()) {
            input.put("first_frame_url", firstFrameUrl);
            input.put("image_url", firstFrameUrl);
        }

        // 2) last_frame_asset_id -> last_frame_url
        Object lastFrameAssetId = firstNonNull(inputPayload.get("last_frame_asset_id"), nodeInput.get("last_frame_asset_id"));
        String lastFrameUrl = resolveAssetUrl(lastFrameAssetId, userId);
        if (lastFrameUrl != null && !lastFrameUrl.isBlank()) {
            input.put("last_frame_url", lastFrameUrl);
        }

        // 3) multi_image_ids -> image_urls（多图）
        List<Object> multiImageIds = toMutableList(firstNonNull(inputPayload.get("multi_image_ids"), nodeInput.get("multi_image_ids")));
        List<String> multiImageUrls = resolveAssetUrls(multiImageIds, userId);
        if (!multiImageUrls.isEmpty()) {
            input.put("image_urls", multiImageUrls);
            if (!input.containsKey("image_url")) {
                input.put("image_url", multiImageUrls.get(0));
            }
        }

        // 4) drive_audio_asset_id -> drive_audio_url / audio_url
        Object driveAudioAssetId = firstNonNull(inputPayload.get("drive_audio_asset_id"), nodeInput.get("drive_audio_asset_id"));
        String driveAudioUrl = resolveAssetUrl(driveAudioAssetId, userId);
        if (driveAudioUrl != null && !driveAudioUrl.isBlank()) {
            input.put("drive_audio_url", driveAudioUrl);
            input.put("audio_url", driveAudioUrl);
        }

        return input;
    }

    // 旧逻辑：兼容 kie_video_task
    private void submitLegacyKieTask(WorkflowRunNode node, Long userId, String prompt, Object inputAssetId) {
        Map<String, Object> nodeInput = readJsonMap(node.getInputJson());
        Map<String, Object> params = toMutableMap(nodeInput.get("params"));
        String generationMode = asString(nodeInput.get("mode"), asString(params.get("generation_mode"), "first_frame"));
        params.put("generation_mode", generationMode);

        String model = asString(params.get("model"), "grok-imagine/text-to-video");
        Map<String, Object> input = buildLegacyVideoInput(userId, params, generationMode, inputAssetId, prompt);

        String taskId = kieService.submitTask(model, input);
        node.setProvider("kie");
        node.setProviderTaskId(taskId);
        node.setStatus("running");
        node.setStartedAt(LocalDateTime.now());
        nodeRepository.save(node);
        pollingWorker.schedule(node.getId());
    }

    private Map<String, Object> buildLegacyVideoInput(
            Long userId,
            Map<String, Object> params,
            String generationMode,
            Object inputAssetId,
            String prompt
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (!prompt.isBlank()) input.put("prompt", prompt);
        copyIfPresent(params, input, "duration");
        copyIfPresent(params, input, "resolution");
        copyIfPresent(params, input, "fps");
        copyIfPresent(params, input, "negative_prompt");
        copyIfPresent(params, input, "aspect_ratio");

        List<String> imageUrls = resolveLegacyImageUrls(userId, params, generationMode, inputAssetId);
        if (!imageUrls.isEmpty()) {
            input.put("image_urls", imageUrls);
            input.put("image_url", imageUrls.get(0));
        }
        return input;
    }

    private List<String> resolveLegacyImageUrls(Long userId, Map<String, Object> params, String mode, Object inputAssetId) {
        Object firstFrameAssetId = params.get("first_frame_asset_id");
        Object lastFrameAssetId = params.get("last_frame_asset_id");
        List<Object> gridAssetIds = toMutableList(params.get("grid_asset_ids"));

        if (firstFrameAssetId == null) firstFrameAssetId = inputAssetId;

        List<Object> selectedAssetIds = new ArrayList<>();
        switch (mode) {
            case "first_last_frame" -> {
                if (firstFrameAssetId != null) selectedAssetIds.add(firstFrameAssetId);
                if (lastFrameAssetId != null) selectedAssetIds.add(lastFrameAssetId);
            }
            case "nine_grid" -> {
                if (gridAssetIds.isEmpty() && inputAssetId != null) {
                    selectedAssetIds = new ArrayList<>(List.of(inputAssetId));
                } else {
                    selectedAssetIds = gridAssetIds;
                }
            }
            default -> {
                if (firstFrameAssetId != null) selectedAssetIds.add(firstFrameAssetId);
                else if (inputAssetId != null) selectedAssetIds.add(inputAssetId);
            }
        }

        return resolveAssetUrls(selectedAssetIds, userId);
    }

    private List<String> resolveAssetUrls(List<Object> assetIds, Long userId) {
        List<String> urls = new ArrayList<>();
        for (Object candidateId : assetIds) {
            String url = resolveAssetUrl(candidateId, userId);
            if (url != null && !url.isBlank()) urls.add(url);
        }
        return urls;
    }

    private String resolveAssetUrl(Object assetId, Long userId) {
        if (assetId == null) return null;

        Long parsedId;
        try {
            parsedId = Long.parseLong(String.valueOf(assetId).trim());
        } catch (Exception ignored) {
            return null;
        }

        Asset asset = assetRepository.findById(parsedId).orElse(null);
        if (asset == null || !asset.getUserId().equals(userId)) return null;
        return asset.getFileUrl();
    }

    private void copyPreferred(
            Map<String, Object> primary,
            Map<String, Object> fallback,
            Map<String, Object> target,
            String key
    ) {
        Object value = firstNonNull(primary.get(key), fallback.get(key));
        if (value == null) return;
        if (value instanceof String text && text.isBlank()) return;
        target.put(key, value);
    }

    private Map<String, Object> findNodeData(Map<String, Object> canvas, String nodeType) {
        Object nodesObject = canvas.get("nodes");
        if (!(nodesObject instanceof List<?> nodes)) return Map.of();

        for (Object nodeObject : nodes) {
            if (!(nodeObject instanceof Map<?, ?> nodeMap)) continue;
            if (!nodeType.equals(String.valueOf(nodeMap.get("type")))) continue;

            Object data = nodeMap.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                Map<String, Object> copied = new HashMap<>();
                dataMap.forEach((k, v) -> copied.put(String.valueOf(k), v));
                return copied;
            }
            return Map.of();
        }
        return Map.of();
    }

    private Map<String, Object> readJsonMap(String json) {
        try {
            if (json == null || json.isBlank()) return new HashMap<>();
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        try {
            if (json == null || json.isBlank()) return new HashMap<>();
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
            });
            return parsed == null ? new HashMap<>() : parsed;
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> toMutableMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> copied = new HashMap<>();
            mapValue.forEach((k, v) -> copied.put(String.valueOf(k), v));
            return copied;
        }
        return new HashMap<>();
    }

    private List<Object> toMutableList(Object value) {
        if (value instanceof List<?> listValue) return new ArrayList<>(listValue);
        if (value instanceof String text && !text.isBlank()) {
            String[] parts = text.split(",");
            List<Object> list = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) list.add(trimmed);
            }
            return list;
        }
        return new ArrayList<>();
    }

    private void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        Object value = from.get(key);
        if (value == null) return;
        if (value instanceof String text && text.isBlank()) return;
        to.put(key, value);
    }

    private String asString(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private String resolvePrompt(Map<String, Object> promptNodeData) {
        String mode = asString(promptNodeData.get("mode"), "write_text");
        String text = asString(promptNodeData.get("text"), "");
        String reversePrompt = asString(promptNodeData.get("reverse_prompt_result"), "");
        String textToVideoPrompt = asString(promptNodeData.get("text_to_video_prompt"), "");

        if ("image_to_prompt".equals(mode) && !reversePrompt.isBlank()) return reversePrompt;
        if ("text_to_video".equals(mode) && !textToVideoPrompt.isBlank()) return textToVideoPrompt;
        return text;
    }
}
