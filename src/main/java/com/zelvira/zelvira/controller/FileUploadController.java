package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/upload")
@CrossOrigin(origins = "*")
public class FileUploadController {

    private final FileStorageService storageService;

    public FileUploadController(FileStorageService storageService) {
        this.storageService = storageService;
    }

    @org.springframework.beans.factory.annotation.Value("${app.minio.public-url:${app.minio.url:https://minio.zelvira.co.id}}")
    private String storageUrl;

    @PostMapping
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        System.out.println("Uploading file: " + file.getOriginalFilename());
        String path = storageService.store(file);
        String fullUrl = formatImageUrl(path);
        System.out.println("File uploaded to: " + fullUrl);
        return ResponseEntity.ok(Map.of("url", fullUrl, "path", path));
    }

    private String formatImageUrl(String path) {
        if (path == null || path.trim().isEmpty() || path.startsWith("http")) {
            return path;
        }
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        String base = storageUrl;
        if (base.contains("storage.zelvira.co.id")) {
            base = base.replace("storage.zelvira.co.id", "minio.zelvira.co.id");
        } else if (base.contains("storage.zelvira.co.id")) {
            base = base.replace("storage.zelvira.co.id", "minio.zelvira.co.id");
        } else if (base.contains("minio.zelvira.co.id")) {
            base = base.replace("minio.zelvira.co.id", "minio.zelvira.co.id");
        }
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + cleanPath;
    }
}
