package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.dto.AssetDtos;
import com.aivideo.canvas.entity.Asset;
import com.aivideo.canvas.repository.AssetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AssetService {
    private static final Set<String> SUPPORTED_IMAGE_MODELS = Set.of("google/imagen4", "google/imagen4-fast");

    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final KieService kieService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssetService(AssetRepository assetRepository, StorageService storageService, KieService kieService) {
        this.assetRepository = assetRepository;
        this.storageService = storageService;
        this.kieService = kieService;
    }

    public Asset upload(Long userId, Long projectId, MultipartFile file) throws Exception {
        Map<String, Object> saved = storageService.saveUpload(file, "raw");
        Asset a = new Asset();
        a.setUserId(userId);
        a.setProjectId(projectId);
        a.setFileName(file.getOriginalFilename());
        a.setFileType(file.getContentType() != null && file.getContentType().startsWith("video") ? "video" : "image");
        a.setMimeType(file.getContentType());
        a.setFileSize(((Number) saved.get("size")).longValue());
        a.setStorageType("local");
        a.setStoragePath(String.valueOf(saved.get("storagePath")));
        a.setFileUrl(String.valueOf(saved.get("fileUrl")));
        a.setMetaJson("{}");
        return assetRepository.save(a);
    }

    public AssetDtos.ReversePromptData reversePrompt(Long userId, Long assetId, String hint) {
        if (assetId == null) throw new AppException("VALIDATION_ERROR", "asset_id is required");

        Asset asset = assetRepository.findByIdAndUserId(assetId, userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "asset not found"));

        if (!"image".equalsIgnoreCase(asset.getFileType())) {
            throw new AppException("VALIDATION_ERROR", "only image assets support reverse prompt");
        }

        String prompt = kieService.reversePromptFromImage(asset.getFileUrl(), hint);
        String provider = prompt.startsWith("[fallback]") ? "fallback" : "kie";
        if (provider.equals("fallback")) prompt = prompt.substring("[fallback]".length()).trim();

        return new AssetDtos.ReversePromptData(asset.getId(), asset.getFileUrl(), prompt, provider);
    }

    public AssetDtos.PromptPolishData polishPrompt(String text, String model, String styleHint) {
        String resolvedModel = kieService.resolveTextModel(model);
        String prompt = kieService.polishPrompt(text, resolvedModel, styleHint);
        return new AssetDtos.PromptPolishData(resolvedModel, prompt);
    }

    public AssetDtos.GenerateImageData generateImage(Long userId, Long projectId, AssetDtos.GenerateImageReq req) {
        if (req == null || req.getPrompt() == null || req.getPrompt().isBlank()) {
            throw new AppException("VALIDATION_ERROR", "prompt is required");
        }

        String model = normalizeImageModel(req.getModel());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", req.getPrompt().trim());
        if (req.getNegativePrompt() != null && !req.getNegativePrompt().isBlank()) {
            input.put("negative_prompt", req.getNegativePrompt().trim());
        }
        if (req.getAspectRatio() != null && !req.getAspectRatio().isBlank()) {
            input.put("aspect_ratio", req.getAspectRatio().trim());
        }

        String taskId = kieService.submitTask(model, input);
        Map<String, Object> result = waitTaskFinished(taskId);
        List<String> urls = kieService.extractResultUrls(result);
        if (urls.isEmpty()) {
            throw new AppException("KIE_BAD_RESPONSE", "image generation missing result url");
        }

        try {
            String sourceUrl = urls.get(0);
            Map<String, Object> saved = storageService.downloadRemoteFile(sourceUrl, "raw");
            String ext = String.valueOf(saved.getOrDefault("extension", ".png"));
            String contentType = String.valueOf(saved.getOrDefault("contentType", ""));

            Asset asset = new Asset();
            asset.setUserId(userId);
            asset.setProjectId(projectId);
            asset.setFileName("generated_" + System.currentTimeMillis() + ext);
            asset.setFileType("image");
            asset.setMimeType(contentType == null || contentType.isBlank() ? "image/png" : contentType);
            asset.setFileSize(((Number) saved.getOrDefault("size", 0)).longValue());
            asset.setStorageType("local");
            asset.setStoragePath(String.valueOf(saved.get("storagePath")));
            asset.setFileUrl(String.valueOf(saved.get("fileUrl")));
            asset.setMetaJson(objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "task_id", taskId,
                    "source_url", sourceUrl
            )));
            Asset created = assetRepository.save(asset);

            return new AssetDtos.GenerateImageData(created.getId(), created.getFileUrl(), model, taskId);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("IMAGE_GENERATE_FAILED", e.getMessage());
        }
    }

    private Map<String, Object> waitTaskFinished(String taskId) {
        for (int attempt = 1; attempt <= 40; attempt++) {
            Map<String, Object> raw = kieService.queryTask(taskId);
            String status = kieService.resolveTaskStatus(raw);
            if ("success".equals(status)) {
                return raw;
            }
            if ("failed".equals(status)) {
                throw new AppException("KIE_TASK_FAILED", extractFailureMessage(raw));
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AppException("KIE_TASK_INTERRUPTED", "image generation interrupted");
            }
        }

        throw new AppException("KIE_TASK_TIMEOUT", "image generation timeout");
    }

    private String extractFailureMessage(Map<String, Object> raw) {
        Object direct = raw.get("message");
        if (direct == null) direct = raw.get("msg");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct);
        }

        Object data = raw.get("data");
        if (data instanceof Map<?, ?> map) {
            Object reason = map.get("failReason");
            if (reason == null) reason = map.get("fail_reason");
            if (reason != null && !String.valueOf(reason).isBlank()) {
                return String.valueOf(reason);
            }
        }
        return "KIE task failed";
    }

    private String normalizeImageModel(String model) {
        String resolved = model == null ? "google/imagen4-fast" : model.trim();
        if (resolved.equalsIgnoreCase("google")) {
            resolved = "google/imagen4-fast";
        }
        if (!SUPPORTED_IMAGE_MODELS.contains(resolved)) {
            throw new AppException("VALIDATION_ERROR", "unsupported image model: " + model);
        }
        return resolved;
    }
}
