package com.privatechatserver.service;

import com.privatechatserver.db.entity.UploadedFileEntity;
import com.privatechatserver.db.repository.UploadedFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageRoot;
    private final UploadedFileRepository repository;

    public FileStorageService(
            @Value("${app.files.storage-path}") String storagePath,
            UploadedFileRepository repository) throws IOException {
        this.storageRoot = Paths.get(storagePath);
        this.repository = repository;
        Files.createDirectories(storageRoot);
    }

    @Transactional
    public String store(String recipient, byte[] data) throws IOException {
        String fileId = UUID.randomUUID().toString();
        Path filePath = storageRoot.resolve(fileId);
        Files.write(filePath, data);

        UploadedFileEntity entity = new UploadedFileEntity();
        entity.setId(fileId);
        entity.setRecipient(recipient);
        entity.setFilePath(filePath.toString());
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);

        return fileId;
    }

    @Transactional
    public byte[] fetchAndDelete(String fileId) throws IOException {
        UploadedFileEntity entity = repository.findById(fileId)
                .orElseThrow(() -> new NoSuchElementException("File not found: " + fileId));

        Path filePath = Paths.get(entity.getFilePath());
        byte[] data = Files.readAllBytes(filePath);

        Files.deleteIfExists(filePath);
        repository.delete(entity);

        return data;
    }
}
