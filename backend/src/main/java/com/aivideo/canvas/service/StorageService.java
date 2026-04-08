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
        return saveBytes(resp.body(), UUID.randomUUID().toString().replace("-","") + ".mp4", subDir);
    }

    public String buildPublicUrl(String relativePath){
        return appProperties.getPublicBaseUrl().replaceAll("/$","") + "/files/" + relativePath;
    }
}
