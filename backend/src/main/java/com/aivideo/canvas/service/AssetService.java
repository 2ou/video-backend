package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.dto.AssetDtos;
import com.aivideo.canvas.entity.Asset;
import com.aivideo.canvas.repository.AssetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AssetService {
    private static final Set<String> SUPPORTED_IMAGE_MODELS = Set.of(
            "google/imagen4",
            "google/imagen4-fast",
            "google/nano-banana-pro",
            "google/nano-banana-2"
    );

    private final AssetRepository assetRepository;
    private final StorageService storageService;
    private final KieService kieService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssetService(AssetRepository assetRepository, StorageService storageService, KieService kieService) {
        this.assetRepository = assetRepository;
        this.storageService = storageService;
        this.kieService = kieService;
    }

    public AssetDtos.UploadTicketData getUploadTicket(Long userId, Long projectId, AssetDtos.UploadTicketReq req) {
        if (req == null) throw new AppException("VALIDATION_ERROR", "request body is required");
        if (req.getFileName() == null || req.getFileName().isBlank()) {
            throw new AppException("VALIDATION_ERROR", "file_name is required");
        }
        if (req.getFileSize() == null || req.getFileSize() <= 0) {
            throw new AppException("VALIDATION_ERROR", "file_size must be greater than 0");
        }

        String mimeType = normalizeMimeType(req.getMimeType());
        StorageService.UploadTicket ticket = storageService.createUploadTicket(userId, projectId, req.getFileName(), mimeType);
        return new AssetDtos.UploadTicketData(
                ticket.getUploadUrl(),
                ticket.getObjectKey(),
                ticket.getFileUrl(),
                ticket.getMethod(),
                ticket.getExpireAt(),
                ticket.getHeaders()
        );
    }

    public AssetDtos.ConfirmUploadData confirmUpload(Long userId, AssetDtos.ConfirmUploadReq req) {
        if (req == null) throw new AppException("VALIDATION_ERROR", "request body is required");
        if (req.getObjectKey() == null || req.getObjectKey().isBlank()) {
            throw new AppException("VALIDATION_ERROR", "object_key is required");
        }
        // Prevent client from confirming objects outside the current user's key namespace.
        if (!storageService.isUserObjectKey(userId, req.getObjectKey())) {
            throw new AppException("VALIDATION_ERROR", "object_key is not allowed for current user");
        }

        Asset existed = assetRepository.findByUserIdAndStoragePath(userId, req.getObjectKey()).orElse(null);
        if (existed != null) {
            return new AssetDtos.ConfirmUploadData(existed.getId(), existed.getFileUrl());
        }

        // Confirm step reads object metadata from OSS to avoid trusting client-side file size/type.
        StorageService.StoredObjectInfo objectInfo = storageService.getObjectInfo(req.getObjectKey());
        if (req.getFileSize() != null && req.getFileSize() > 0 && !req.getFileSize().equals(objectInfo.getSize())) {
            throw new AppException("VALIDATION_ERROR", "file_size mismatch");
        }

        String resolvedName = normalizeFileName(req.getFileName(), req.getObjectKey());
        String mimeType = normalizeMimeType(req.getMimeType(), objectInfo.getContentType());
        String fileType = resolveFileType(mimeType);

        Asset asset = new Asset();
        asset.setUserId(userId);
        asset.setProjectId(req.getProjectId());
        asset.setFileName(resolvedName);
        asset.setFileType(fileType);
        asset.setMimeType(mimeType);
        asset.setFileSize(objectInfo.getSize());
        asset.setStorageType(storageService.getStorageType());
        asset.setStoragePath(req.getObjectKey());
        asset.setFileUrl(storageService.buildPublicUrl(req.getObjectKey()));
        asset.setMetaJson(buildConfirmMeta(req.getObjectKey(), objectInfo));
        Asset created = assetRepository.save(asset);
        return new AssetDtos.ConfirmUploadData(created.getId(), created.getFileUrl());
    }

    public Asset getAsset(Long userId, Long assetId) {
        if (assetId == null) throw new AppException("VALIDATION_ERROR", "asset_id is required");
        return assetRepository.findByIdAndUserId(assetId, userId)
                .orElseThrow(() -> new AppException("NOT_FOUND", "asset not found"));
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
            asset.setStorageType(storageService.getStorageType());
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
        if (resolved.equalsIgnoreCase("nano-banana-pro")) {
            resolved = "google/nano-banana-pro";
        }
        if (resolved.equalsIgnoreCase("nano-banana-2")) {
            resolved = "google/nano-banana-2";
        }
        if (!SUPPORTED_IMAGE_MODELS.contains(resolved)) {
            throw new AppException("VALIDATION_ERROR", "unsupported image model: " + model);
        }
        return resolved;
    }

    private String normalizeMimeType(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "application/octet-stream";
    }

    private String resolveFileType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) return "image";
        String lower = mimeType.toLowerCase();
        if (lower.startsWith("video/")) return "video";
        if (lower.startsWith("audio/")) return "audio";
        return "image";
    }

    private String normalizeFileName(String fileName, String objectKey) {
        if (fileName != null && !fileName.isBlank()) return fileName.trim();
        int slash = objectKey == null ? -1 : objectKey.lastIndexOf('/');
        if (slash >= 0 && slash < objectKey.length() - 1) {
            return objectKey.substring(slash + 1);
        }
        return "upload_" + System.currentTimeMillis();
    }

    private String buildConfirmMeta(String objectKey, StorageService.StoredObjectInfo objectInfo) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "source", "direct_upload",
                    "object_key", objectKey,
                    "etag", objectInfo.getEtag() == null ? "" : objectInfo.getEtag()
            ));
        } catch (Exception e) {
            return "{}";
        }
    }
}
