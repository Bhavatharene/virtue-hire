package com.virtuehire.controller;

import com.virtuehire.model.Candidate;
import com.virtuehire.model.CandidateAccessRequest;
import com.virtuehire.model.CandidateAccessRequestStatus;
import com.virtuehire.model.Hr;
import com.virtuehire.model.Question;
import com.virtuehire.service.CandidateService;
import com.virtuehire.service.CandidateAccessRequestService;
import com.virtuehire.service.HrService;
import com.virtuehire.service.QuestionService;
import com.virtuehire.service.AssessmentResultService;
import com.virtuehire.service.AssessmentService;
import com.virtuehire.model.Assessment;
import com.virtuehire.model.AssessmentSection;
import com.virtuehire.repository.AssessmentSectionRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/hrs")
@CrossOrigin(origins = { "https://admin.virtuehire.in", "https://backend.virtuehire.in", "http://localhost:3000" }, allowCredentials = "true")
public class HrRestController {

    private static final Logger logger = LoggerFactory.getLogger(HrRestController.class);

    private final HrService hrService;
    private final CandidateService candidateService;
    private final CandidateAccessRequestService candidateAccessRequestService;
    private final QuestionService questionService;
    private final AssessmentResultService assessmentResultService;
    private final AssessmentService assessmentService;
    private final AssessmentSectionRepository assessmentSectionRepository;
    private final Path uploadDir;

    public HrRestController(HrService hrService, CandidateService candidateService,
            CandidateAccessRequestService candidateAccessRequestService,
            QuestionService questionService, AssessmentResultService assessmentResultService,
            AssessmentService assessmentService,
            AssessmentSectionRepository assessmentSectionRepository,
            @Value("${file.upload-dir}") String uploadDirPath) {
        this.hrService = hrService;
        this.candidateService = candidateService;
        this.candidateAccessRequestService = candidateAccessRequestService;
        this.questionService = questionService;
        this.assessmentResultService = assessmentResultService;
        this.assessmentService = assessmentService;
        this.assessmentSectionRepository = assessmentSectionRepository;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    // ------------------ REGISTER HR ------------------
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> registerHr(
            @ModelAttribute Hr hr,
            @RequestParam("idProof") MultipartFile idProofFile) throws IOException {

        if (!hr.getPassword().equals(hr.getConfirmPassword()))
            return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));

        if (hrService.findByEmail(hr.getEmail()).isPresent())
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered"));

        if (!Files.exists(uploadDir))
            Files.createDirectories(uploadDir);

        if (idProofFile != null && !idProofFile.isEmpty()) {
            String fileName = System.currentTimeMillis() + "_" + idProofFile.getOriginalFilename();
            Path path = uploadDir.resolve(fileName);
            idProofFile.transferTo(path.toFile());
            hr.setIdProofPath(fileName);
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "ID proof is required"));
        }

        hrService.save(hr);
        String message = "Registration successful!";
        try {
            hrService.sendVerificationMail(hr);
            message += " Please verify your email first using the code sent to your email, then wait for admin approval.";
        } catch (Exception ex) {
            logger.error("HR registered but verification email failed for {}", hr.getEmail(), ex);
            message += " We could not send the verification email right now. Please try again later.";
        }
        return ResponseEntity.ok(Map.of("message", message));
    }

    // ------------------ VERIFY HR EMAIL ------------------
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        boolean verified = hrService.verifyEmail(email, code);
        if (verified) {
            return ResponseEntity
                    .ok(Map.of("message", "Email verified successfully! Admin will now review your application."));
        } else {
            return ResponseEntity.status(400).body(Map.of("error", "Invalid verification code"));
        }
    }

    // ------------------ LOGIN HR ------------------
    @PostMapping("/login")
    public ResponseEntity<?> loginHr(@RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        Hr hr = hrService.login(email, password);
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));

        if (!hr.getVerified())
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Your account is not verified yet. Please wait for admin approval."));

        // Store HR in session
        session.setAttribute("hr", hr);

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "hr", hr));
    }

    // ------------------ HR DASHBOARD ------------------
    @GetMapping("/dashboard")
    public ResponseEntity<?> hrDashboard(HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        if (hr.getId() != null && hr.getId() > 0) {
            hr = hrService.findById(hr.getId()).orElse(null);
            session.setAttribute("hr", hr);
        }

        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "HR account not found"));

        return ResponseEntity.ok(Map.of(
                "hr", hr,
                "planDisplay", hrService.getPlanDisplayName(hr)));
    }

    // ------------------ GET ALL CANDIDATES ------------------
    @GetMapping("/candidates")
    public ResponseEntity<?> getCandidates(HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        hr = refreshHr(hr, session);
        List<Candidate> candidates = candidateService.findAll();
        Map<Long, CandidateAccessRequestStatus> statuses = candidateAccessRequestService.findStatusesForHr(hr.getId());
        return ResponseEntity.ok(Map.of(
                "hr", hr,
                "candidates", candidates.stream()
                        .map(candidate -> toHrCandidateSummary(candidate, statuses.get(candidate.getId())))
                        .toList()));
    }

    // ------------------ VIEW SINGLE CANDIDATE ------------------
    @GetMapping("/candidates/{candidateId}")
    public ResponseEntity<?> viewCandidate(@PathVariable Long candidateId, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        hr = refreshHr(hr, session);

        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null)
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));

        CandidateAccessRequestStatus requestStatus = candidateAccessRequestService.findStatus(hr.getId(), candidateId);
        if (requestStatus != CandidateAccessRequestStatus.APPROVED) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Contact Admin to view full details");
            response.put("requestStatus", requestStatus == null ? "NONE" : requestStatus.name());
            response.put("hasAccess", false);
            candidateAccessRequestService.findRequest(hr.getId(), candidateId)
                    .ifPresent(request -> response.put("requestId", request.getId()));
            return ResponseEntity.status(403).body(response);
        }

        // Build enriched result list with real section name from AssessmentSection
        var rawResults = assessmentResultService.getCandidateResults(candidateId);
        List<Map<String, Object>> enrichedResults = new ArrayList<>();
        for (var r : rawResults) {
            // AssessmentResult.subject = assessmentName, .level = sectionNumber
            // AssessmentSection.subject = real section subject (e.g. "Aptitude",
            // "Vocabulary")
            String sectionName = assessmentSectionRepository
                    .findByAssessmentNameAndSectionNumber(r.getSubject(), r.getLevel())
                    .map(AssessmentSection::getSubject)
                    .orElse("Section " + r.getLevel());

            Map<String, Object> row = new HashMap<>();
            row.put("subject", r.getSubject());
            row.put("level", r.getLevel());
            row.put("score", r.getScore());
            row.put("attemptedAt", r.getAttemptedAt());
            row.put("sectionName", sectionName);
            enrichedResults.add(row);
        }

        return ResponseEntity.ok(Map.of(
                "candidate", candidate,
                "detailedResults", enrichedResults,
                "canView", true,
                "requestStatus", CandidateAccessRequestStatus.APPROVED.name()));
    }

    @GetMapping("/candidates/{candidateId}/summary")
    public ResponseEntity<?> getCandidateSummary(@PathVariable Long candidateId, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        hr = refreshHr(hr, session);
        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        }

        CandidateAccessRequestStatus requestStatus = candidateAccessRequestService.findStatus(hr.getId(), candidateId);
        return ResponseEntity.ok(Map.of(
                "candidate", toHrCandidateSummary(candidate, requestStatus),
                "requestStatus", requestStatus == null ? "NONE" : requestStatus.name(),
                "hasAccess", requestStatus == CandidateAccessRequestStatus.APPROVED));
    }

    @PostMapping("/candidates/{candidateId}/access-request")
    public ResponseEntity<?> requestCandidateAccess(@PathVariable Long candidateId, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        hr = refreshHr(hr, session);
        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        }

        CandidateAccessRequest request = candidateAccessRequestService.createOrRefreshRequest(hr, candidate);
        return ResponseEntity.ok(Map.of(
                "message", request.getStatus() == CandidateAccessRequestStatus.APPROVED
                        ? "Access is already approved for this candidate."
                        : "Access request submitted successfully.",
                "requestStatus", request.getStatus().name(),
                "requestId", request.getId()));
    }

    // ------------------ DOWNLOAD RESUME ------------------
    @GetMapping("/candidates/{candidateId}/resume")
    public ResponseEntity<Resource> downloadResume(
            @PathVariable Long candidateId,
            @RequestParam(defaultValue = "attachment") String disposition,
            HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).build();
        }

        hr = refreshHr(hr, session);
        if (!candidateAccessRequestService.hasApprovedAccess(hr.getId(), candidateId)) {
            return ResponseEntity.status(403).build();
        }

        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null || candidate.getResumePath() == null)
            return ResponseEntity.notFound().build();

        try {
            Path filePath = uploadDir.resolve(candidate.getResumePath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists())
                return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(filePath);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            String normalizedDisposition = "inline".equalsIgnoreCase(disposition) ? "inline" : "attachment";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, normalizedDisposition + "; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ------------------ SEARCH / FILTER CANDIDATES ------------------
    @GetMapping("/candidates/search")
    public ResponseEntity<?> searchCandidates(
            @RequestParam(required = false) String skills,
            @RequestParam(required = false) String experienceLevel,
            @RequestParam(required = false) Integer minScore,
            HttpSession session) {

        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        hr = refreshHr(hr, session);
        List<Candidate> candidates = candidateService.searchCandidates(skills, experienceLevel, minScore);
        Map<Long, CandidateAccessRequestStatus> statuses = candidateAccessRequestService.findStatusesForHr(hr.getId());

        Map<String, Object> filters = new HashMap<>();
        filters.put("skills", skills == null ? "" : skills);
        filters.put("experienceLevel", experienceLevel == null ? "" : experienceLevel);
        filters.put("minScore", minScore);

        return ResponseEntity.ok(Map.of(
                "hr", hr,
                "candidates", candidates.stream()
                        .map(candidate -> toHrCandidateSummary(candidate, statuses.get(candidate.getId())))
                        .toList(),
                "filters", filters));
    }

    @GetMapping("/dashboard/candidates/search")
    public ResponseEntity<?> searchCandidatesForDashboard(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false, defaultValue = "all") String experience,
            @RequestParam(required = false, defaultValue = "") String scoreSort,
            HttpSession session) {

        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        hr = refreshHr(hr, session);
        List<Candidate> candidates = candidateService.searchCandidatesForHrDashboard(
                name,
                skill,
                experience,
                scoreSort);
        Map<Long, CandidateAccessRequestStatus> statuses = candidateAccessRequestService.findStatusesForHr(hr.getId());

        return ResponseEntity.ok(Map.of(
                "candidates", candidates.stream()
                        .map(candidate -> toHrCandidateSummary(candidate, statuses.get(candidate.getId())))
                        .toList(),
                "filters", Map.of(
                        "name", name == null ? "" : name,
                        "skill", skill == null ? "" : skill,
                        "experience", experience,
                        "scoreSort", scoreSort == null ? "" : scoreSort)));
    }

    // ------------------ QUESTION UPLOAD ------------------
    @PostMapping("/questions/upload-csv")
    public ResponseEntity<?> uploadQuestions(@RequestParam("file") MultipartFile file,
            @RequestParam("testName") String testName,
            @RequestParam(value = "input1", required = false) String input1,
            @RequestParam(value = "output1", required = false) String output1,
            @RequestParam(value = "input2", required = false) String input2,
            @RequestParam(value = "output2", required = false) String output2,
            HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        try {
            questionService.saveQuestionsFromUpload(file, testName, input1, output1, input2, output2);
            return ResponseEntity.ok(Map.of("message", "Questions uploaded successfully for " + testName));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error uploading CSV: " + e.getMessage()));
        }
    }

    // ------------------ ASSESSMENT CONFIG ------------------
    @PostMapping("/assessments/create")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> createAssessment(@RequestBody Map<String, Object> payload, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        try {
            String assessmentName = (String) payload.get("assessmentName");
            String description = (String) payload.getOrDefault("description", "");
            List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");

            if (assessmentName == null || assessmentName.trim().isEmpty() || sections == null || sections.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid assessment data."));
            }

            Assessment assessment = assessmentService.createAssessment(assessmentName, description, sections);
            return ResponseEntity
                    .ok(Map.of("message", "Assessment created successfully", "assessmentId", assessment.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create assessment: " + e.getMessage()));
        }
    }

    @GetMapping("/subjects")
    public ResponseEntity<?> getSubjects() {
        return ResponseEntity.ok(Map.of("subjects", questionService.getAllSubjects()));
    }

    @GetMapping("/subjects-info")
    public ResponseEntity<?> getSubjectsInfo(HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        List<String> subjects = questionService.getAllSubjects();
        List<Map<String, Object>> infoList = new ArrayList<>();

        for (String sub : subjects) {
            List<Question> questions = questionService.getQuestionsBySubject(sub);
            int count = questions.size();
            int compilerCount = (int) questions.stream().filter(Question::isHasCompiler).count();
            int noCompilerCount = count - compilerCount;
            infoList.add(Map.of(
                    "subject", sub,
                    "count", count,
                    "compilerCount", compilerCount,
                    "noCompilerCount", noCompilerCount));
        }
        return ResponseEntity.ok(Map.of("subjects", infoList));
    }

    // ------------------ GET QUESTIONS FOR BANK ------------------
    @GetMapping("/questions/bank")
    public ResponseEntity<?> getQuestionsBySubject(@RequestParam String subject, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        List<Question> questions = questionService.getQuestionsBySubject(subject);
        return ResponseEntity.ok(Map.of("questions", questions));
    }

    // ------------------ GET DISTINCT SECTIONS FOR SUBJECT ------------------
    @GetMapping("/questions/sections")
    public ResponseEntity<?> getQuestionSections(@RequestParam String subject, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        List<Question> questions = questionService.getQuestionsBySubject(subject);

        // Group by level and section name to find available counts
        Map<Integer, Map<String, Object>> sectionsMap = new HashMap<>();

        for (Question q : questions) {
            int level = q.getLevel();
            String name = q.getSectionName() != null ? q.getSectionName() : "Phase " + level;

            if (!sectionsMap.containsKey(level)) {
                Map<String, Object> newSection = new HashMap<>();
                newSection.put("level", level);
                newSection.put("sectionName", name);
                newSection.put("availableQuestions", 0);
                sectionsMap.put(level, newSection);
            }

            Map<String, Object> sectionData = sectionsMap.get(level);
            sectionData.put("availableQuestions", (int) sectionData.get("availableQuestions") + 1);
        }

        List<Map<String, Object>> sections = new ArrayList<>(sectionsMap.values());
        sections.sort(Comparator.comparingInt(s -> (int) s.get("level")));

        return ResponseEntity.ok(Map.of("sections", sections));
    }

    // ------------------ LIVE ASSESSMENTS OVERVIEW ------------------
    @GetMapping("/assessments/live")
    public ResponseEntity<?> getLiveAssessments(HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        List<Assessment> assessments = assessmentService.getAllAssessments();
        List<Map<String, Object>> liveList = new ArrayList<>();

        for (Assessment a : assessments) {
            List<AssessmentSection> sections = assessmentService.getAssessmentSections(a.getId());
            int totalQuestions = sections.stream().mapToInt(AssessmentSection::getQuestionCount).sum();
            int totalTime = sections.stream().mapToInt(AssessmentSection::getSectionTime).sum();

            liveList.add(Map.of(
                    "id", a.getId(),
                    "assessmentName", a.getAssessmentName(),
                    "description", a.getDescription() != null ? a.getDescription() : "",
                    "sectionCount", sections.size(),
                    "totalQuestions", totalQuestions,
                    "totalTime", totalTime,
                    "isLocked", a.isLocked()));
        }

        return ResponseEntity.ok(Map.of("assessments", liveList));
    }

    // ------------------ DELETE ASSESSMENT ------------------
    @DeleteMapping("/assessments/{id}")
    public ResponseEntity<?> deleteAssessment(@PathVariable Long id, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        try {
            assessmentService.deleteAssessment(id);
            return ResponseEntity.ok(Map.of("message", "Assessment deleted successfully."));
        } catch (Exception e) {
            logger.error("Failed to delete assessment {}", id, e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage() != null && !e.getMessage().isBlank()
                            ? e.getMessage()
                            : "Failed to delete assessment."));
        }
    }

    // ------------------ LOCK/UNLOCK ASSESSMENT ------------------
    @PutMapping("/assessments/{id}/lock")
    public ResponseEntity<?> toggleLock(@PathVariable Long id, @RequestParam boolean lock, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        try {
            assessmentService.toggleLock(id, lock);
            return ResponseEntity
                    .ok(Map.of("message", "Assessment " + (lock ? "locked" : "unlocked") + " successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update assessment status."));
        }
    }

    // ------------------ CANDIDATE RESULTS ------------------
    @GetMapping("/candidates/results")
    public ResponseEntity<?> getAllCandidateResults(HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        Hr refreshedHr = refreshHr(hr, session);
        List<Candidate> candidates = candidateService.findAll().stream()
                .filter(candidate -> candidateAccessRequestService.hasApprovedAccess(refreshedHr.getId(), candidate.getId()))
                .toList();
        List<Map<String, Object>> results = new ArrayList<>();

        for (Candidate c : candidates) {
            if (c.getAssessmentTaken() != null && c.getAssessmentTaken()) {
                Map<String, Object> data = new HashMap<>();
                data.put("candidateId", c.getId());
                data.put("fullName", c.getFullName());
                data.put("email", c.getEmail());
                data.put("badge", c.getBadge());
                data.put("detailedResults", assessmentResultService.getCandidateResults(c.getId()));
                results.add(data);
            }
        }
        return ResponseEntity.ok(Map.of("results", results));
    }

    @GetMapping("/candidates/{candidateId}/results")
    public ResponseEntity<?> getSingleCandidateResults(@PathVariable Long candidateId, HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        hr = refreshHr(hr, session);
        if (!candidateAccessRequestService.hasApprovedAccess(hr.getId(), candidateId)) {
            return ResponseEntity.status(403).body(Map.of("error", "Contact Admin to view full details"));
        }

        return ResponseEntity.ok(Map.of(
                "results", assessmentResultService.getCandidateResults(candidateId)));
    }

    // ------------------ LOGOUT ------------------
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    // ------------------ SERVE FILE ------------------
    @GetMapping("/file/{filename:.+}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename, HttpSession session) {
        try {
            Object role = session.getAttribute("role");
            if (!"ADMIN".equals(role) && session.getAttribute("hr") == null) {
                return ResponseEntity.status(403).build();
            }

            Path filePath = uploadDir.resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists())
                return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(filePath);
            if (contentType == null)
                contentType = "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private Hr refreshHr(Hr hr, HttpSession session) {
        Hr latest = hr.getId() == null ? hr : hrService.findById(hr.getId()).orElse(hr);
        session.setAttribute("hr", latest);
        return latest;
    }

    private Map<String, Object> toHrCandidateSummary(Candidate candidate, CandidateAccessRequestStatus status) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", candidate.getId());
        summary.put("fullName", candidate.getFullName());
        summary.put("role", candidate.getBadge() != null && !candidate.getBadge().isBlank()
                ? candidate.getBadge()
                : candidate.getExperienceLevel() != null && !candidate.getExperienceLevel().isBlank()
                        ? candidate.getExperienceLevel()
                        : "Candidate");
        summary.put("experience", candidate.getExperience() != null ? candidate.getExperience() : 0);
        summary.put("hasAccess", status == CandidateAccessRequestStatus.APPROVED);
        summary.put("requestStatus", status == null ? "NONE" : status.name());
        return summary;
    }
}
