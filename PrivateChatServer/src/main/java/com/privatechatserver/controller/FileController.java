package com.privatechatserver.controller;

import com.privatechatserver.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
public class FileController {

    private final long maxFileSizeBytes;
    private final FileStorageService fileStorageService;

    public FileController(
            @Value("${app.files.max-size-bytes}") long maxFileSizeBytes,
            FileStorageService fileStorageService) {
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("recipient") String recipient,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        if (file.getSize() > maxFileSizeBytes) {
            return ResponseEntity.badRequest().body(Map.of("error", "File too large"));
        }

        if (recipient == null || recipient.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing recipient"));
        }

        String fileId = fileStorageService.store(recipient, file.getBytes());
        System.out.println("Fichero subido para " + recipient + ": " + fileId);
        return ResponseEntity.ok(Map.of("fileId", fileId));
    }

    @GetMapping("/file/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String fileId) {
        try {
            byte[] data = fileStorageService.fetchAndDelete(fileId);
            System.out.println("Fichero descargado y borrado: " + fileId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
                    .body(data);
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            System.out.println("Error al leer fichero " + fileId + ": " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
