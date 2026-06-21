package com.clearleaf.api;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class MinioStorageService {
    private final MinioClient client;
    private final String bucket;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public MinioStorageService(
            @Value("${app.storage.minio.endpoint}") String endpoint,
            @Value("${app.storage.minio.access-key}") String accessKey,
            @Value("${app.storage.minio.secret-key}") String secretKey,
            @Value("${app.storage.minio.bucket}") String bucket) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    public MediaUploadResponse upload(MultipartFile file) {
        requireFile(file);
        try {
            ensureBucket();
            String objectKey = objectKey(file.getOriginalFilename());
            String contentType = file.getContentType() == null || file.getContentType().isBlank()
                    ? "application/octet-stream"
                    : file.getContentType();
            String etag = client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build()).etag();
            return new MediaUploadResponse(bucket, objectKey, sanitizeFilename(file.getOriginalFilename()), contentType, file.getSize(), etag);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to upload file", ex);
        }
    }

    public String readText(String objectKey) {
        try (InputStream input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(requireObjectKey(objectKey)).build())) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to read uploaded file", ex);
        }
    }

    public StoredMedia readMedia(String objectKey) {
        String key = requireObjectKey(objectKey);
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            try (InputStream input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                String contentType = stat.contentType() == null || stat.contentType().isBlank()
                        ? "application/octet-stream"
                        : stat.contentType();
                return new StoredMedia(input.readAllBytes(), contentType);
            }
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code()) || "NoSuchObject".equals(ex.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Uploaded file was not found", ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to read uploaded file", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to read uploaded file", ex);
        }
    }

    public boolean exists(String objectKey) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(requireObjectKey(objectKey)).build());
            return true;
        } catch (ErrorResponseException ex) {
            if ("NoSuchKey".equals(ex.errorResponse().code()) || "NoSuchObject".equals(ex.errorResponse().code())) {
                return false;
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to inspect uploaded file", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to inspect uploaded file", ex);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            bucketReady.set(true);
        }
    }

    private void requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }
    }

    private String objectKey(String originalFilename) {
        String datePrefix = LocalDate.now().toString();
        return "uploads/" + datePrefix + "/" + UUID.randomUUID() + "-" + sanitizeFilename(originalFilename);
    }

    private String sanitizeFilename(String name) {
        String value = name == null || name.isBlank() ? "file" : name.trim().toLowerCase(Locale.ROOT);
        return value.replaceAll("[^a-z0-9._-]+", "-");
    }

    private String requireObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "objectKey is required");
        }
        return objectKey.trim();
    }
}
