package com.clearleaf.api;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/media")
public class StudentMediaController {
    private final MinioStorageService storage;

    public StudentMediaController(MinioStorageService storage) {
        this.storage = storage;
    }

    @GetMapping
    public ResponseEntity<byte[]> read(@RequestParam("objectKey") String objectKey) {
        StoredMedia media = storage.readMedia(objectKey);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(media.bytes());
    }
}
