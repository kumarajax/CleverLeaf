package com.clearleaf.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/admin/media")
public class MediaController {
    private final MinioStorageService storage;

    public MediaController(MinioStorageService storage) {
        this.storage = storage;
    }

    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public MediaUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return storage.upload(file);
    }
}
