package com.virtuehire.service;

import com.virtuehire.model.ExamActivity;
import com.virtuehire.repository.ExamActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class ExamMonitoringService {

    @Autowired
    private ExamActivityRepository repository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void logAndBroadcastActivity(ExamActivity activity) {
        activity.setTimestamp(LocalDateTime.now());
        
        // Determine status based on violations
        if (activity.getViolationCount() != null) {
            if (activity.getViolationCount() >= 3) {
                activity.setStatus("Suspicious");
            } else if (activity.getViolationCount() > 0) {
                activity.setStatus("Warning");
            } else {
                activity.setStatus("Active");
            }
        }
        
        if ("EXAM_SUBMITTED".equals(activity.getEventType())) {
            activity.setStatus("Submitted");
        }

        // Save to DB
        repository.save(activity);

        // Broadcast to HR via WebSocket
        messagingTemplate.convertAndSend("/topic/exam-monitoring", activity);
    }
}
