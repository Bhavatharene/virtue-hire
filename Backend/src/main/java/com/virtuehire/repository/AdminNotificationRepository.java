package com.virtuehire.repository;

import com.virtuehire.model.AdminNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {
    Optional<AdminNotification> findByTypeAndReferenceKeyAndResolvedFalse(String type, String referenceKey);

    long countByTypeAndResolvedFalse(String type);

    List<AdminNotification> findTop10ByTypeAndResolvedFalseOrderByCreatedAtDesc(String type);

    List<AdminNotification> findAllByTypeAndReferenceKeyAndResolvedFalse(String type, String referenceKey);
}
