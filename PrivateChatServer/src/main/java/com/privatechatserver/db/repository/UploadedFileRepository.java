package com.privatechatserver.db.repository;

import com.privatechatserver.db.entity.UploadedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFileEntity, String> {
}
