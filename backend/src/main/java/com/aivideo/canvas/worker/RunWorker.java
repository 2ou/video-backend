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
            Map<String, Object> canvas = objectMapper.readValue(version.getCanvasJson(), new TypeReference<>() {
            });
            Map<String, Object> promptNodeData = findNodeData(canvas, "prompt_input");
            Map<String, Object> imageNodeData = findNodeData(canvas, "input_video");

            String prompt = resolvePrompt(promptNodeData);
            String imageMode = asString(imageNodeData.get("mode"), "");
            String imageToImagePrompt = asString(imageNodeData.get("image_to_image_prompt"), "");
            if ("image_to_image".equals(imageMode) && !imageToImagePrompt.isBlank()) {
                prompt = prompt.isBlank() ? imageToImagePrompt : prompt + ", " + imageToImagePrompt;
            }
            Object inputAssetId = imageNodeData.get("asset_id");

            boolean hasKieNode = false;

            for (WorkflowRunNode node : runNodes) {
                if (!"kie_video_task".equals(node.getNodeType())) {
                    node.setStatus("success");
                    nodeRepository.save(node);
                    continue;
                }

                hasKieNode = true;
                Map<String, Object> nodeInput = readJsonMap(node.getInputJson());
                Map<String, Object> params = toMutableMap(nodeInput.get("params"));
                String generationMode = asString(nodeInput.get("mode"), asString(params.get("generation_mode"), "first_frame"));
                params.put("generation_mode", generationMode);

                String model = asString(params.get("model"), "grok-imagine/text-to-video");
                Map<String, Object> input = buildVideoInput(run.getUserId(), params, generationMode, inputAssetId, prompt);

                String taskId = kieService.submitTask(model, input);
                node.setProvider("kie");
                node.setProviderTaskId(taskId);
                node.setStatus("running");
                node.setStartedAt(LocalDateTime.now());
                nodeRepository.save(node);

                pollingWorker.schedule(node.getId());
            }

            if (hasKieNode) {
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

    private Map<String, Object> buildVideoInput(
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

        List<String> imageUrls = resolveImageUrls(userId, params, generationMode, inputAssetId);
        if (!imageUrls.isEmpty()) {
            input.put("image_urls", imageUrls);
            input.put("image_url", imageUrls.get(0));
        }

        return input;
    }

    private List<String> resolveImageUrls(Long userId, Map<String, Object> params, String mode, Object inputAssetId) {
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

        List<String> imageUrls = new ArrayList<>();
        for (Object candidateId : selectedAssetIds) {
            String url = resolveAssetUrl(candidateId, userId);
            if (url != null && !url.isBlank()) imageUrls.add(url);
        }
        return imageUrls;
    }

    private String resolveAssetUrl(Object assetId, Long userId) {
        if (assetId == null) return null;

        Long parsedId;
        try {
            parsedId = Long.parseLong(String.valueOf(assetId));
        } catch (Exception ignored) {
            return null;
        }

        Asset asset = assetRepository.findById(parsedId).orElse(null);
        if (asset == null || !asset.getUserId().equals(userId)) return null;
        return asset.getFileUrl();
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
