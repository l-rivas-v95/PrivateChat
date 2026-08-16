package com.privatechatserver.service;

import com.privatechatserver.db.entity.PendingMessageEntity;
import com.privatechatserver.db.repository.PendingMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PendingMessageService {

    private final PendingMessageRepository repository;

    public PendingMessageService(PendingMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(String recipient, String payload) {
        PendingMessageEntity entity = new PendingMessageEntity();
        entity.setRecipient(recipient);
        entity.setPayload(payload);
        entity.setCreatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Transactional
    public List<String> fetchAndDelete(String recipient) {
        List<PendingMessageEntity> messages = repository.findByRecipientOrderByCreatedAtAsc(recipient);
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        repository.deleteAll(messages);
        return messages.stream()
                .map(PendingMessageEntity::getPayload)
                .collect(Collectors.toList());
    }
}
