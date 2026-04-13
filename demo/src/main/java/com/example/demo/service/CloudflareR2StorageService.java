package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

@Service
public class CloudflareR2StorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicBaseUrl;
    private final String objectPrefix;

    public CloudflareR2StorageService(
            @Value("${cloudflare.r2.endpoint}") String endpoint,
            @Value("${cloudflare.r2.region:auto}") String region,
            @Value("${cloudflare.r2.bucket}") String bucketName,
            @Value("${cloudflare.r2.public-base-url}") String publicBaseUrl,
            @Value("${cloudflare.r2.object-prefix:}") String objectPrefix,
            @Value("${cloudflare.r2.access-key-id}") String accessKeyId,
            @Value("${cloudflare.r2.secret-access-key}") String secretAccessKey) {

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        this.bucketName = bucketName;
        this.publicBaseUrl = trimTrailingSlash(publicBaseUrl);
        this.objectPrefix = normalizeFolder(objectPrefix);
    }

    public String uploadImage(MultipartFile file, String folder, String fileNameSeed) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File ảnh không hợp lệ");
        }

        String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
        String normalizedFolder = normalizeFolder(folder);
        String normalizedFileName = sanitizeSegment(fileNameSeed);

        String objectKey = normalizedFolder.isBlank()
                ? normalizedFileName + extension
                : normalizedFolder + "/" + normalizedFileName + extension;

        if (!objectPrefix.isBlank()) {
            objectKey = objectPrefix + "/" + objectKey;
        }

        byte[] payload;
        try {
            payload = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc dữ liệu file ảnh", exception);
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(resolveContentType(file.getContentType(), extension))
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(payload));
        return publicBaseUrl + "/" + objectKey;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            int lastDot = originalFilename.lastIndexOf('.');
            return originalFilename.substring(lastDot).toLowerCase(Locale.ROOT);
        }

        if (contentType == null || contentType.isBlank()) {
            return ".png";
        }

        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return ".jpg";
        }
        if (contentType.contains("webp")) {
            return ".webp";
        }
        if (contentType.contains("gif")) {
            return ".gif";
        }
        if (contentType.contains("svg")) {
            return ".svg";
        }
        return ".png";
    }

    private String resolveContentType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }

        return switch (extension) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".webp" -> "image/webp";
            case ".gif" -> "image/gif";
            case ".svg" -> "image/svg+xml";
            default -> "image/png";
        };
    }

    private String normalizeFolder(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isBlank()) {
            return "";
        }

        String[] segments = normalized.split("/");
        StringBuilder builder = new StringBuilder();
        for (String segment : segments) {
            String safe = sanitizeSegment(segment);
            if (safe.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(safe);
        }
        return builder.toString();
    }

    private String sanitizeSegment(String value) {
        String base = value == null ? "" : value.trim();
        if (base.isBlank()) {
            base = "file-" + UUID.randomUUID().toString().substring(0, 8);
        }

        String normalized = base.replaceAll("[^A-Za-z0-9._-]", "-");
        return normalized.isBlank() ? "file-" + UUID.randomUUID().toString().substring(0, 8) : normalized;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
