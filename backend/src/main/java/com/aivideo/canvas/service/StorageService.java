package com.aivideo.canvas.service;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class StorageService {
    private static final Pattern HTTP_PREFIX = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private final AppProperties appProperties;

    public StorageService(AppProperties appProperties) { this.appProperties = appProperties; }

    public UploadTicket createUploadTicket(Long userId, Long projectId, String fileName, String mimeType) {
        validateOssConfig();
        String bucket = required("app.oss.bucket", appProperties.getOss().getBucket());
        // Put every user upload under a user-scoped prefix so confirm step can enforce ownership.
        String objectKey = buildUserObjectKey(userId, projectId, fileName, mimeType);
        Date expiration = new Date(System.currentTimeMillis() + ticketExpireMillis());

        OSS oss = newClient();
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.PUT);
            request.setExpiration(expiration);
            if (mimeType != null && !mimeType.isBlank()) {
                request.setContentType(mimeType);
            }
            URL uploadUrl = oss.generatePresignedUrl(request);
            Map<String, String> headers = new LinkedHashMap<>();
            if (mimeType != null && !mimeType.isBlank()) {
                headers.put("Content-Type", mimeType);
            }
            return new UploadTicket(uploadUrl.toString(), objectKey, buildPublicUrl(objectKey), "PUT", expiration.getTime(), headers);
        } catch (OSSException | ClientException e) {
            throw new AppException("OSS_TICKET_FAILED", e.getMessage());
        } finally {
            safeShutdown(oss);
        }
    }

    public StoredObjectInfo getObjectInfo(String objectKey) {
        validateOssConfig();
        String bucket = required("app.oss.bucket", appProperties.getOss().getBucket());
        OSS oss = newClient();
        try {
            ObjectMetadata meta = oss.getObjectMetadata(bucket, objectKey);
            return new StoredObjectInfo(meta.getContentLength(), meta.getETag(), meta.getContentType());
        } catch (OSSException e) {
            if ("NoSuchKey".equalsIgnoreCase(e.getErrorCode())) {
                throw new AppException("ASSET_NOT_FOUND", "uploaded object not found on OSS");
            }
            throw new AppException("OSS_HEAD_OBJECT_FAILED", e.getMessage());
        } catch (ClientException e) {
            throw new AppException("OSS_HEAD_OBJECT_FAILED", e.getMessage());
        } finally {
            safeShutdown(oss);
        }
    }

    public boolean isUserObjectKey(Long userId, String objectKey) {
        if (userId == null || objectKey == null || objectKey.isBlank()) return false;
        String keyPrefix = normalizeKeyPrefix(appProperties.getOss().getKeyPrefix());
        String expectPrefix = keyPrefix + "/u" + userId + "/";
        return objectKey.startsWith(expectPrefix);
    }

    public String getStorageType() {
        return "oss";
    }

    public Map<String,Object> downloadRemoteFile(String url, String subDir) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 400) {
            throw new AppException("REMOTE_DOWNLOAD_FAILED", "download remote file failed, status=" + resp.statusCode());
        }

        String contentType = resp.headers()
                .firstValue("Content-Type")
                .map(v -> v.split(";")[0].trim())
                .orElse("");
        String ext = guessExtension(contentType, url);
        String objectKey = buildSystemObjectKey(subDir, ext);
        putObject(objectKey, resp.body(), contentType);
        String fileUrl = buildPublicUrl(objectKey);
        return Map.of(
                "relativePath", objectKey,
                "storagePath", objectKey,
                "fileUrl", fileUrl,
                "size", resp.body().length,
                "contentType", contentType,
                "extension", ext
        );
    }

    public String buildPublicUrl(String objectKey){
        String customBase = appProperties.getOss().getPublicBaseUrl();
        if (customBase != null && !customBase.isBlank()) {
            return trimRightSlash(customBase) + "/" + objectKey;
        }

        String bucket = required("app.oss.bucket", appProperties.getOss().getBucket());
        String endpoint = required("app.oss.endpoint", appProperties.getOss().getEndpoint());
        String cleanEndpoint = HTTP_PREFIX.matcher(endpoint.trim()).replaceFirst("");
        return "https://" + bucket + "." + cleanEndpoint + "/" + objectKey;
    }

    private String buildUserObjectKey(Long userId, Long projectId, String fileName, String mimeType) {
        if (userId == null) throw new AppException("VALIDATION_ERROR", "user id is required");
        String keyPrefix = normalizeKeyPrefix(appProperties.getOss().getKeyPrefix());
        String projectSegment = projectId != null && projectId > 0 ? "p" + projectId + "/" : "";
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String ext = normalizeExtension(fileName, mimeType);
        return keyPrefix + "/u" + userId + "/" + projectSegment + dateDir + "/" + randomName(ext);
    }

    private String buildSystemObjectKey(String subDir, String extension) {
        String keyPrefix = normalizeKeyPrefix(appProperties.getOss().getKeyPrefix());
        String dateDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String safeSubDir = sanitizeSegment(subDir);
        return keyPrefix + "/system/" + safeSubDir + "/" + dateDir + "/" + randomName(extension);
    }

    private void putObject(String objectKey, byte[] content, String contentType) {
        validateOssConfig();
        String bucket = required("app.oss.bucket", appProperties.getOss().getBucket());
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        if (contentType != null && !contentType.isBlank()) {
            metadata.setContentType(contentType);
        }
        OSS oss = newClient();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            oss.putObject(new PutObjectRequest(bucket, objectKey, inputStream, metadata));
        } catch (OSSException | ClientException | IOException e) {
            throw new AppException("OSS_UPLOAD_FAILED", e.getMessage());
        } finally {
            safeShutdown(oss);
        }
    }

    private OSS newClient() {
        String endpoint = required("app.oss.endpoint", appProperties.getOss().getEndpoint());
        String accessKeyId = required("app.oss.access-key-id", appProperties.getOss().getAccessKeyId());
        String accessKeySecret = required("app.oss.access-key-secret", appProperties.getOss().getAccessKeySecret());
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }

    private void validateOssConfig() {
        required("app.oss.endpoint", appProperties.getOss().getEndpoint());
        required("app.oss.bucket", appProperties.getOss().getBucket());
        required("app.oss.access-key-id", appProperties.getOss().getAccessKeyId());
        required("app.oss.access-key-secret", appProperties.getOss().getAccessKeySecret());
    }

    private long ticketExpireMillis() {
        Integer seconds = appProperties.getOss().getTicketExpireSeconds();
        int safeSeconds = (seconds == null || seconds <= 0) ? 900 : seconds;
        return safeSeconds * 1000L;
    }

    private String normalizeKeyPrefix(String keyPrefix) {
        String prefix = (keyPrefix == null || keyPrefix.isBlank()) ? "assets" : keyPrefix.trim();
        prefix = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        return prefix.isBlank() ? "assets" : prefix;
    }

    private String normalizeExtension(String fileName, String mimeType) {
        String extension = "";
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot >= 0 && dot < fileName.length() - 1) {
                extension = fileName.substring(dot).toLowerCase();
            }
        }
        if (extension.isBlank() || extension.length() > 10) {
            extension = guessExtension(mimeType, fileName);
        }
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        return extension.toLowerCase();
    }

    private String sanitizeSegment(String segment) {
        if (segment == null || segment.isBlank()) return "misc";
        return segment.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String randomName(String extension) {
        return UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private String required(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new AppException("CONFIG_ERROR", key + " is required");
        }
        return value.trim();
    }

    private String trimRightSlash(String value) {
        return value.replaceAll("/+$", "");
    }

    private void safeShutdown(OSS oss) {
        if (oss == null) return;
        try {
            oss.shutdown();
        } catch (Exception ignored) {
        }
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

    public static class UploadTicket {
        private final String uploadUrl;
        private final String objectKey;
        private final String fileUrl;
        private final String method;
        private final Long expireAt;
        private final Map<String, String> headers;

        public UploadTicket(String uploadUrl, String objectKey, String fileUrl, String method, Long expireAt, Map<String, String> headers) {
            this.uploadUrl = uploadUrl;
            this.objectKey = objectKey;
            this.fileUrl = fileUrl;
            this.method = method;
            this.expireAt = expireAt;
            this.headers = headers;
        }

        public String getUploadUrl() { return uploadUrl; }
        public String getObjectKey() { return objectKey; }
        public String getFileUrl() { return fileUrl; }
        public String getMethod() { return method; }
        public Long getExpireAt() { return expireAt; }
        public Map<String, String> getHeaders() { return headers; }
    }

    public static class StoredObjectInfo {
        private final Long size;
        private final String etag;
        private final String contentType;

        public StoredObjectInfo(Long size, String etag, String contentType) {
            this.size = size;
            this.etag = etag;
            this.contentType = contentType;
        }

        public Long getSize() { return size; }
        public String getEtag() { return etag; }
        public String getContentType() { return contentType; }
    }
}
