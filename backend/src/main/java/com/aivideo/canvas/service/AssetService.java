package com.aivideo.canvas.service;

import com.aivideo.canvas.entity.Asset;
import com.aivideo.canvas.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class AssetService {
    private final AssetRepository assetRepository;
    private final StorageService storageService;

    public AssetService(AssetRepository assetRepository, StorageService storageService) {
        this.assetRepository = assetRepository;
        this.storageService = storageService;
    }

    public Asset upload(Long userId, Long projectId, MultipartFile file) throws Exception {
        Map<String,Object> saved = storageService.saveUpload(file, "raw");
        Asset a = new Asset();
        a.setUserId(userId); a.setProjectId(projectId); a.setFileName(file.getOriginalFilename());
        a.setFileType(file.getContentType() != null && file.getContentType().startsWith("video") ? "video" : "image");
        a.setMimeType(file.getContentType()); a.setFileSize(((Number)saved.get("size")).longValue());
        a.setStorageType("local"); a.setStoragePath(String.valueOf(saved.get("storagePath"))); a.setFileUrl(String.valueOf(saved.get("fileUrl")));
        a.setMetaJson("{}");
        return assetRepository.save(a);
    }
}
