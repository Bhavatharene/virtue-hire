package com.virtuehire.service;

import com.virtuehire.model.AdminNotification;
import com.virtuehire.model.Candidate;
import com.virtuehire.repository.AdminNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminNotificationService {

    public static final String TYPE_COMBINED_ASSESSMENT = "COMBINED_ASSESSMENT_REQUIRED";

    private final AdminNotificationRepository adminNotificationRepository;

    public AdminNotificationService(AdminNotificationRepository adminNotificationRepository) {
        this.adminNotificationRepository = adminNotificationRepository;
    }

    @Transactional
    public void createCombinedAssessmentNotification(Candidate candidate, List<String> displaySkills, String referenceKey) {
        if (referenceKey == null || referenceKey.isBlank()) {
            return;
        }

        adminNotificationRepository.findByTypeAndReferenceKeyAndResolvedFalse(TYPE_COMBINED_ASSESSMENT, referenceKey)
                .orElseGet(() -> adminNotificationRepository.save(new AdminNotification(
                        TYPE_COMBINED_ASSESSMENT,
                        buildCombinedAssessmentMessage(candidate, displaySkills),
                        referenceKey)));
    }

    @Transactional
    public void resolveCombinedAssessmentNotification(String referenceKey) {
        if (referenceKey == null || referenceKey.isBlank()) {
            return;
        }

        List<AdminNotification> notifications = adminNotificationRepository
                .findAllByTypeAndReferenceKeyAndResolvedFalse(TYPE_COMBINED_ASSESSMENT, referenceKey);

        for (AdminNotification notification : notifications) {
            notification.setResolved(true);
            notification.setResolvedAt(LocalDateTime.now());
        }

        if (!notifications.isEmpty()) {
            adminNotificationRepository.saveAll(notifications);
        }
    }

    public long countOpenCombinedAssessmentNotifications() {
        return adminNotificationRepository.countByTypeAndResolvedFalse(TYPE_COMBINED_ASSESSMENT);
    }

    public List<AdminNotification> getOpenCombinedAssessmentNotifications() {
        return adminNotificationRepository.findTop10ByTypeAndResolvedFalseOrderByCreatedAtDesc(TYPE_COMBINED_ASSESSMENT);
    }

    private String buildCombinedAssessmentMessage(Candidate candidate, List<String> displaySkills) {
        String skills = String.join(", ", displaySkills);
        if (candidate != null && candidate.getFullName() != null && !candidate.getFullName().isBlank()) {
            return "A candidate has registered with skills: " + skills + ". A combined assessment needs to be created.";
        }
        return "A candidate has registered with skills: " + skills + ". A combined assessment needs to be created.";
    }
}
