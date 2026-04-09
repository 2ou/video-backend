package com.aivideo.canvas.service;

import com.aivideo.canvas.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageService {
    private final AppProperties appProperties;

    public StorageService(AppProperties appProperties) { this.appProperties = appProperties; }

    public Map<String,Object> saveUpload(MultipartFile file, String subDir) throws IOException {
        String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".") ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.')) : "";
        String filename = UUID.randomUUID().toString().replace("-","") + ext;
        return saveBytes(file.getBytes(), filename, subDir);
    }

    public Map<String,Object> saveBytes(byte[] content, String filename, String subDir) throws IOException {
        Path full = Path.of(appProperties.getUploadDir(), subDir, filename);
        Files.createDirectories(full.getParent());
        Files.write(full, content);
        String relative = subDir + "/" + filename;
        return Map.of("relativePath", relative, "storagePath", full.toString(), "fileUrl", buildPublicUrl(relative), "size", content.length);
    }

    public Map<String,Object> downloadRemoteFile(String url, String subDir) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String contentType = resp.headers()
                .firstValue("Content-Type")
                .map(v -> v.split(";")[0].trim())
                .orElse("");
        String ext = guessExtension(contentType, url);
        Map<String, Object> saved = saveBytes(resp.body(), UUID.randomUUID().toString().replace("-","") + ext, subDir);
        return Map.of(
                "relativePath", saved.get("relativePath"),
                "storagePath", saved.get("storagePath"),
                "fileUrl", saved.get("fileUrl"),
                "size", saved.get("size"),
                "contentType", contentType,
                "extension", ext
        );
    }

    public String buildPublicUrl(String relativePath){
        return appProperties.getPublicBaseUrl().replaceAll("/$","") + "/files/" + relativePath;
    }

    private String guessExtension(String contentType, String url) {
        if (contentType != null && !contentType.isBlank()) {
            if (contentType.contains("mp4")) return ".mp4";
            if (contentType.contains("webm")) return ".webm";
            if (contentType.contains("quicktime")) return ".mov";
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
            if (contentType.contains("webp")) return ".webp";
            if (contentType.contains("gif")) return ".gif";
        }

        if (url != null) {
            String lower = url.toLowerCase();
            for (String ext : List.of(".mp4", ".webm", ".mov", ".png", ".jpg", ".jpeg", ".webp", ".gif")) {
                if (lower.contains(ext)) return ext.equals(".jpeg") ? ".jpg" : ext;
            }
        }
        return ".bin";
    }
}
