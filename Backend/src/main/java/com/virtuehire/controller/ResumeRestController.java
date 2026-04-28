package com.virtuehire.controller;

import com.virtuehire.model.Candidate;
import com.virtuehire.service.ResumeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/candidates/resumes")
public class ResumeRestController {

    private final ResumeService resumeService;

    public ResumeRestController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @GetMapping
    public ResponseEntity<?> listResumes(HttpSession session) {
        Candidate candidate = getSessionCandidate(session);
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        return ResponseEntity.ok(Map.of("resumes", resumeService.listCandidateResumes(candidate.getId())));
    }

    @GetMapping("/{resumeId}")
    public ResponseEntity<?> getResume(@PathVariable Long resumeId, HttpSession session) {
        Candidate candidate = getSessionCandidate(session);
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        return ResponseEntity.ok(Map.of("resume", resumeService.getCandidateResume(candidate.getId(), resumeId)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createResume(@RequestBody Map<String, Object> payload, HttpSession session) throws IOException {
        Candidate candidate = getSessionCandidate(session);
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Resume created successfully",
                "resume", resumeService.createResume(candidate, payload)
        ));
    }

    @PutMapping(value = "/{resumeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateResume(@PathVariable Long resumeId,
                                          @RequestBody Map<String, Object> payload,
                                          HttpSession session) throws IOException {
        Candidate candidate = getSessionCandidate(session);
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        return ResponseEntity.ok(Map.of(
                "message", "Resume updated successfully",
                "resume", resumeService.updateResume(candidate.getId(), resumeId, payload)
        ));
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<?> deleteResume(@PathVariable Long resumeId, HttpSession session) {
        Candidate candidate = getSessionCandidate(session);
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }
        resumeService.deleteResume(candidate.getId(), resumeId);
        return ResponseEntity.ok(Map.of("message", "Resume deleted successfully"));
    }

    @GetMapping("/{resumeId}/pdf")
    public ResponseEntity<Resource> accessPdf(@PathVariable Long resumeId,
                                              @RequestParam(defaultValue = "inline") String disposition,
                                              HttpSession session) throws IOException {
        Candidate candidate = getSessionCandidate(session);
        if (candidate == null) {
            return ResponseEntity.status(401).build();
        }
        Path filePath = resumeService.resolveResumePdf(candidate.getId(), resumeId);
        Resource resource = new UrlResource(filePath.toUri());
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_PDF_VALUE;
        }

        String fileName = resumeService.getResumePdfName(candidate.getId(), resumeId);
        String headerValue = ("attachment".equalsIgnoreCase(disposition) ? "attachment" : "inline")
                + "; filename=\"" + fileName + "\"";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private Candidate getSessionCandidate(HttpSession session) {
        return (Candidate) session.getAttribute("candidate");
    }
}
