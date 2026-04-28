package com.virtuehire.controller;

import com.virtuehire.model.ExamActivity;
import com.virtuehire.service.ExamMonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/monitoring")
@CrossOrigin(origins = "https://admin.virtuehire.in", allowCredentials = "true")
public class ExamActivityController {

    @Autowired
    private ExamMonitoringService monitoringService;

    @PostMapping("/log")
    public ResponseEntity<?> logActivity(@RequestBody ExamActivity activity) {
        monitoringService.logAndBroadcastActivity(activity);
        return ResponseEntity.ok().build();
    }
}
