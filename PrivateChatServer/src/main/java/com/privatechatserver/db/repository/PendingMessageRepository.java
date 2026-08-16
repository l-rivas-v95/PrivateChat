package com.privatechatserver.db.repository;

import com.privatechatserver.db.entity.PendingMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PendingMessageRepository extends JpaRepository<PendingMessageEntity, Long> {

    List<PendingMessageEntity> findByRecipientOrderByCreatedAtAsc(String recipient);
}
