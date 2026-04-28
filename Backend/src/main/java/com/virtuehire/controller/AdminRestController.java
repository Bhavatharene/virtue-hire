package com.virtuehire.controller;

import com.virtuehire.model.*;
import com.virtuehire.service.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminRestController {

    private final HrService hrService;
    private final PaymentService paymentService;
    private final CandidateService candidateService;
    private final CandidateAccessRequestService candidateAccessRequestService;
    private final QuestionService questionService;
    private final AssessmentResultService assessmentResultService;
    private final AssessmentService assessmentService;
    private final AdminNotificationService adminNotificationService;
    private final Path uploadDir;

    public AdminRestController(HrService hrService, PaymentService paymentService,
            CandidateService candidateService, CandidateAccessRequestService candidateAccessRequestService,
            QuestionService questionService,
            AssessmentResultService assessmentResultService, AssessmentService assessmentService,
            AdminNotificationService adminNotificationService,
            @Value("${file.upload-dir}") String uploadDirPath) {
        this.hrService = hrService;
        this.paymentService = paymentService;
        this.candidateService = candidateService;
        this.candidateAccessRequestService = candidateAccessRequestService;
        this.questionService = questionService;
        this.assessmentResultService = assessmentResultService;
        this.assessmentService = assessmentService;
        this.adminNotificationService = adminNotificationService;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    private ResponseEntity<Map<String, String>> requireAdmin(HttpSession session) {
        Object role = session.getAttribute("role");
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }
        return null;
    }

    // ---------------------- DASHBOARD ------------------------
    @GetMapping("/dashboard")
    public ResponseEntity<?> adminDashboard() {

        List<Hr> allHrs = hrService.findAll();
        List<Candidate> allCandidates = candidateService.findAll();
        List<Payment> allPayments = paymentService.getAllPayments();

        long totalHrs = allHrs.size();
        long verifiedHrs = allHrs.stream().filter(hr -> Boolean.TRUE.equals(hr.getVerified())).count();
        long unverifiedHrs = allHrs.stream()
                .filter(hr -> Boolean.TRUE.equals(hr.getEmailVerified()) && !Boolean.TRUE.equals(hr.getVerified()))
                .count();

        long totalCandidates = allCandidates.size();
        long candidatesWithTest = allCandidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.getAssessmentTaken()))
                .count();

        long pendingCandidates = allCandidates.stream()
                .filter(c -> Boolean.FALSE.equals(c.getApproved()))
                .count();

        Map<String, Object> paymentStats = paymentService.getPaymentStatistics();

        double totalRevenue = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();

        Map<String, Object> response = new HashMap<>();
        response.put("hrs", allHrs);
        response.put("candidates", allCandidates);
        response.put("payments", allPayments);
        response.put("totalHrs", totalHrs);
        response.put("verifiedHrs", verifiedHrs);
        response.put("unverifiedHrs", unverifiedHrs);
        response.put("totalCandidates", totalCandidates);
        response.put("candidatesWithTest", candidatesWithTest);
        response.put("pendingCandidates", pendingCandidates);
        response.put("paymentStats", paymentStats);
        response.put("totalRevenue", totalRevenue);
        response.put("pendingAccessRequests", candidateAccessRequestService.countPending());
        response.put("pendingCombinedAssessmentRequests", adminNotificationService.countOpenCombinedAssessmentNotifications());
        response.put("combinedAssessmentNotifications", adminNotificationService.getOpenCombinedAssessmentNotifications().stream()
                .map(notification -> Map.of(
                        "id", notification.getId(),
                        "message", notification.getMessage(),
                        "createdAt", notification.getCreatedAt()))
                .toList());

        return ResponseEntity.ok(response);
    }

    // ---------------------- HR LIST ------------------------
    @GetMapping("/hrs")
    public ResponseEntity<?> showAllHrs(@RequestParam(required = false, defaultValue = "all") String filter) {

        List<Hr> hrs;

        if ("verified".equals(filter)) {
            hrs = hrService.findAll().stream()
                    .filter(hr -> Boolean.TRUE.equals(hr.getVerified()))
                    .toList();
        } else if ("unverified".equals(filter)) {
            hrs = hrService.findAll().stream()
                    .filter(hr -> Boolean.TRUE.equals(hr.getEmailVerified()) && !Boolean.TRUE.equals(hr.getVerified()))
                    .toList();
        } else {
            hrs = hrService.findAll();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("hrs", hrs);
        response.put("filter", filter);

        return ResponseEntity.ok(response);
    }

    // ---------------------- PENDING CANDIDATES ------------------------
    @GetMapping("/candidates/pending")
    public ResponseEntity<?> showPendingCandidates() {
        List<Candidate> pendingCandidates = candidateService.findAll().stream()
                .filter(c -> Boolean.FALSE.equals(c.getApproved()))
                .toList();
        return ResponseEntity.ok(Map.of("candidates", pendingCandidates));
    }

    // ---------------------- APPROVE CANDIDATE ------------------------
    @PostMapping("/candidates/approve/{id}")
    public ResponseEntity<?> approveCandidate(@PathVariable Long id) {

        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate != null) {
            int currentYear = java.time.Year.now().getValue();
            if (candidate.getYearOfGraduation() != null && candidate.getYearOfGraduation() <= currentYear + 1) {
                candidate.setApproved(true);
                candidateService.save(candidate);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Candidate approved"));
    }

    // ---------------------- REJECT CANDIDATE ------------------------
    @PostMapping("/candidates/reject/{id}")
    public ResponseEntity<?> rejectCandidate(@PathVariable Long id,
            @RequestParam String reason) {

        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate != null) {
            candidate.setRejectionReason(reason);
            candidate.setApproved(false);
            candidateService.save(candidate);
        }

        return ResponseEntity.ok(Map.of("message", "Candidate rejected"));
    }

    // ---------------------- VERIFY HR ------------------------
    @PostMapping("/verify/{id}")
    public ResponseEntity<?> verifyHr(@PathVariable Long id) {
        Hr hr = hrService.findById(id).orElse(null);
        if (hr != null) {
            hr.setVerified(true);
            hrService.save(hr);
            try {
                hrService.sendApprovalMail(hr);
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(Map.of("message", "HR verified"));
    }

    // ---------------------- UNVERIFY HR ------------------------
    @PostMapping("/unverify/{id}")
    public ResponseEntity<?> unverifyHr(@PathVariable Long id) {
        Hr hr = hrService.findById(id).orElse(null);
        if (hr != null) {
            hr.setVerified(false);
            hrService.save(hr);
        }
        return ResponseEntity.ok(Map.of("message", "HR unverified"));
    }

    @DeleteMapping("/hrs/{id}")
    public ResponseEntity<?> deleteHr(@PathVariable Long id) {
        try {
            hrService.deleteHrById(id);
            return ResponseEntity.ok(Map.of("message", "HR deleted successfully"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(Map.of(
                    "error",
                    ex.getMessage() != null && !ex.getMessage().isBlank()
                            ? ex.getMessage()
                            : "Failed to delete HR account"));
        }
    }

    // ---------------------- QUESTIONS ------------------------
    @GetMapping("/questions")
    public ResponseEntity<?> questionManagement(@RequestParam(required = false) String subject) {

        List<Question> questions = (subject != null && !subject.trim().isEmpty())
                ? questionService.getQuestionsBySubject(subject)
                : questionService.getAllQuestionsFromRepository();

        List<String> subjects = questionService.getAllSubjects();

        Map<String, Object> response = new HashMap<>();
        response.put("questions", questions);
        response.put("subjects", subjects);
        response.put("selectedSubject", subject);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/questions/add")
    public ResponseEntity<?> addQuestion(@RequestBody Question question) {
        questionService.saveQuestionViaRepository(question);
        return ResponseEntity.ok(Map.of("message", "Question added"));
    }

    @PostMapping("/questions/upload")
    public ResponseEntity<?> uploadQuestions(@RequestParam("file") MultipartFile file,
            @RequestParam("testName") String testName,
            @RequestParam(value = "input1", required = false) String input1,
            @RequestParam(value = "output1", required = false) String output1,
            @RequestParam(value = "input2", required = false) String input2,
            @RequestParam(value = "output2", required = false) String output2) {
        try {
            questionService.saveQuestionsFromUpload(file, testName, input1, output1, input2, output2);
            return ResponseEntity.ok(Map.of("message", "Questions uploaded successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Failed to upload: " + e.getMessage()));
        }
    }

    @PostMapping("/assessment/config")
    public ResponseEntity<?> saveAssessmentConfig(@RequestBody List<AssessmentConfig> configs) {
        try {
            questionService.saveConfigs(configs);
            return ResponseEntity.ok(Map.of("message", "Configuration saved"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to save: " + e.getMessage()));
        }
    }

    @GetMapping("/assessment/config/{subject}")
    public ResponseEntity<?> getAssessmentConfig(@PathVariable String subject) {
        return ResponseEntity.ok(questionService.getConfigs(subject));
    }

    @GetMapping("/questions/edit/{id}")
    public ResponseEntity<?> getQuestion(@PathVariable Long id) {
        Question question = questionService.getQuestionByIdFromRepository(id);
        if (question != null)
            return ResponseEntity.ok(question);
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/questions/update/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable Long id,
            @RequestBody Question updatedData) {
        Question existing = questionService.getQuestionByIdFromRepository(id);
        if (existing != null) {
            existing.setSubject(updatedData.getSubject());
            existing.setSectionName(updatedData.getSectionName());
            existing.setLevel(updatedData.getLevel());
            existing.setText(updatedData.getText());
            existing.setOptions(updatedData.getOptions());
            existing.setCorrectAnswer(updatedData.getCorrectAnswer());
            questionService.saveQuestionViaRepository(existing);
        }
        return ResponseEntity.ok(Map.of("message", "Question updated"));
    }

    @PostMapping("/questions/delete/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long id) {
        questionService.deleteQuestionViaRepository(id);
        return ResponseEntity.ok(Map.of("message", "Question deleted"));
    }

    // ---------------------- CANDIDATE DETAILS ------------------------
    @GetMapping("/candidates/{id}")
    public ResponseEntity<?> viewCandidate(@PathVariable Long id) {
        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate == null)
            return ResponseEntity.notFound().build();

        List<AssessmentResult> results = assessmentResultService.getCandidateResults(id);

        return ResponseEntity.ok(Map.of(
                "candidate", candidate,
                "results", results));
    }

    @GetMapping("/candidates")
    public ResponseEntity<?> getAllCandidates() {
        return ResponseEntity.ok(Map.of("candidates", candidateService.findAll()));
    }

    @DeleteMapping("/candidates/{id}")
    public ResponseEntity<?> deleteCandidate(@PathVariable Long id) {
        try {
            candidateService.deleteCandidateById(id);
            return ResponseEntity.ok(Map.of("message", "Candidate deleted successfully"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/candidates/{id}")
    public ResponseEntity<?> updateCandidate(@PathVariable Long id, @RequestBody Candidate payload) {
        Candidate candidate = candidateService.findById(id).orElse(null);
        if (candidate == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        }

        if (payload.getEmail() != null && !payload.getEmail().isBlank()) {
            Candidate existingByEmail = candidateService.findByEmail(payload.getEmail());
            if (existingByEmail != null && !existingByEmail.getId().equals(candidate.getId())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already registered. Please use a different email."));
            }
        }

        candidate.setFullName(payload.getFullName());
        candidate.setEmail(payload.getEmail());
        candidate.setPhoneNumber(payload.getPhoneNumber());
        candidate.setAlternatePhoneNumber(payload.getAlternatePhoneNumber());
        candidate.setGender(payload.getGender());
        candidate.setDateOfBirth(payload.getDateOfBirth());
        candidate.setCity(payload.getCity());
        candidate.setState(payload.getState());
        candidate.setHighestEducation(payload.getHighestEducation());
        candidate.setCollegeUniversity(payload.getCollegeUniversity());
        candidate.setYearOfGraduation(payload.getYearOfGraduation());
        candidate.setExperience(payload.getExperience());
        candidate.setExperienceLevel(payload.getExperienceLevel());
        candidate.setSkills(payload.getSkills());
        candidate.setBadge(payload.getBadge());
        candidate.setApproved(payload.getApproved());
        candidate.setAssessmentTaken(payload.getAssessmentTaken());
        candidate.setRejectionReason(payload.getRejectionReason());

        Candidate savedCandidate = candidateService.save(candidate);
        return ResponseEntity.ok(Map.of(
                "message", "Candidate updated successfully",
                "candidate", savedCandidate));
    }

    @GetMapping("/candidate-access-requests")
    public ResponseEntity<?> getCandidateAccessRequests(
            @RequestParam(required = false) String status,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        CandidateAccessRequestStatus requestStatus = null;
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            try {
                requestStatus = CandidateAccessRequestStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status filter."));
            }
        }

        List<Map<String, Object>> requests = candidateAccessRequestService.getRequests(requestStatus).stream()
                .map(request -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", request.getId());
                    data.put("status", request.getStatus().name());
                    data.put("createdAt", request.getCreatedAt());
                    data.put("updatedAt", request.getUpdatedAt());
                    data.put("reviewedAt", request.getReviewedAt());
                    data.put("hrId", request.getHr().getId());
                    data.put("hrName", request.getHr().getFullName());
                    data.put("hrEmail", request.getHr().getEmail());
                    data.put("candidateId", request.getCandidate().getId());
                    data.put("candidateName", request.getCandidate().getFullName());
                    data.put("candidateRole", request.getCandidate().getBadge() != null && !request.getCandidate().getBadge().isBlank()
                            ? request.getCandidate().getBadge()
                            : request.getCandidate().getExperienceLevel() != null && !request.getCandidate().getExperienceLevel().isBlank()
                                    ? request.getCandidate().getExperienceLevel()
                                    : "Candidate");
                    data.put("candidateExperience",
                            request.getCandidate().getExperience() != null ? request.getCandidate().getExperience() : 0);
                    return data;
                })
                .toList();

        return ResponseEntity.ok(Map.of("requests", requests));
    }

    @PostMapping("/candidate-access-requests/{requestId}/approve")
    public ResponseEntity<?> approveCandidateAccessRequest(@PathVariable Long requestId, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            candidateAccessRequestService.approve(requestId);
            return ResponseEntity.ok(Map.of("message", "Access request approved."));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/candidate-access-requests/{requestId}/reject")
    public ResponseEntity<?> rejectCandidateAccessRequest(@PathVariable Long requestId, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            candidateAccessRequestService.reject(requestId);
            return ResponseEntity.ok(Map.of("message", "Access request rejected."));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/candidate-access-requests/{requestId}/decline")
    public ResponseEntity<?> declineCandidateAccessRequest(@PathVariable Long requestId, HttpSession session) {
        return rejectCandidateAccessRequest(requestId, session);
    }

    // ---------------------- HR DETAILS ------------------------
    @GetMapping("/hrs/{id}")
    public ResponseEntity<?> viewHrDetails(@PathVariable Long id) {

        Hr hr = hrService.findById(id).orElse(null);
        if (hr == null)
            return ResponseEntity.notFound().build();

        return ResponseEntity.ok(Map.of(
                "hr", hr,
                "payments", paymentService.getPaymentsByHr(id)));
    }

    // ---------------------- DOWNLOAD RESUME ------------------------
    @GetMapping("/download/resume/{candidateId}")
    public ResponseEntity<Resource> downloadResume(@PathVariable Long candidateId) {

        Candidate candidate = candidateService.findById(candidateId).orElse(null);
        if (candidate == null || candidate.getResumePath() == null)
            return ResponseEntity.notFound().build();

        try {
            Path filePath = uploadDir.resolve(candidate.getResumePath()).normalize();

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable())
                return ResponseEntity.notFound().build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    // ---------------------- PAYMENTS ------------------------
    @GetMapping("/payments")
    public ResponseEntity<?> showAllPayments() {

        List<Payment> allPayments = paymentService.getAllPayments();

        double totalRevenue = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount)
                .sum();

        long successfulPayments = allPayments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCESS)
                .count();

        return ResponseEntity.ok(Map.of(
                "payments", allPayments,
                "totalRevenue", totalRevenue,
                "successfulPayments", successfulPayments,
                "totalPayments", allPayments.size()));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<?> viewPaymentDetails(@PathVariable Long id) {
        Payment payment = paymentService.getPaymentById(id);
        if (payment != null)
            return ResponseEntity.ok(payment);
        return ResponseEntity.notFound().build();
    }

    // ---------------------- ADMIN TEST MANAGEMENT ------------------------
    @GetMapping("/subjects-info")
    public ResponseEntity<?> getSubjectsInfo(HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        List<String> subjects = questionService.getAllSubjects();
        List<Map<String, Object>> infoList = new ArrayList<>();

        for (String subject : subjects) {
            List<Question> questions = questionService.getQuestionsBySubject(subject);
            int compilerCount = (int) questions.stream().filter(Question::isHasCompiler).count();
            infoList.add(Map.of(
                    "subject", subject,
                    "count", questions.size(),
                    "compilerCount", compilerCount,
                    "noCompilerCount", questions.size() - compilerCount));
        }

        return ResponseEntity.ok(Map.of("subjects", infoList));
    }

    @PostMapping("/questions/upload-csv")
    public ResponseEntity<?> uploadQuestionsCsv(@RequestParam("file") MultipartFile file,
            @RequestParam("testName") String testName,
            @RequestParam(value = "input1", required = false) String input1,
            @RequestParam(value = "output1", required = false) String output1,
            @RequestParam(value = "input2", required = false) String input2,
            @RequestParam(value = "output2", required = false) String output2,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            questionService.saveQuestionsFromUpload(file, testName, input1, output1, input2, output2);
            return ResponseEntity.ok(Map.of("message", "Questions uploaded successfully for " + testName));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Error uploading CSV: " + e.getMessage()));
        }
    }

    @PostMapping("/assessments/create")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> createAssessment(@RequestBody Map<String, Object> payload, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            String assessmentName = (String) payload.get("assessmentName");
            String description = (String) payload.getOrDefault("description", "");
            List<Map<String, Object>> sections = (List<Map<String, Object>>) payload.get("sections");

            if (assessmentName == null || assessmentName.trim().isEmpty() || sections == null || sections.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid assessment data."));
            }

            Assessment assessment = assessmentService.createAssessment(assessmentName, description, sections);
            return ResponseEntity.ok(Map.of(
                    "message", "Assessment created successfully",
                    "assessmentId", assessment.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to create assessment: " + e.getMessage()));
        }
    }

    @GetMapping("/assessments/live")
    public ResponseEntity<?> getLiveAssessments(HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        List<Assessment> assessments = assessmentService.getAllAssessments();
        List<Map<String, Object>> liveList = new ArrayList<>();

        for (Assessment assessment : assessments) {
            List<AssessmentSection> sections = assessmentService.getAssessmentSections(assessment.getId());
            int totalQuestions = sections.stream().mapToInt(AssessmentSection::getQuestionCount).sum();
            int totalTime = sections.stream().mapToInt(AssessmentSection::getSectionTime).sum();

            liveList.add(Map.of(
                    "id", assessment.getId(),
                    "assessmentName", assessment.getAssessmentName(),
                    "description", assessment.getDescription() != null ? assessment.getDescription() : "",
                    "sectionCount", sections.size(),
                    "totalQuestions", totalQuestions,
                    "totalTime", totalTime,
                    "isLocked", assessment.isLocked()));
        }

        return ResponseEntity.ok(Map.of("assessments", liveList));
    }

    @DeleteMapping("/assessments/{id}")
    public ResponseEntity<?> deleteAssessment(@PathVariable Long id, HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            assessmentService.deleteAssessment(id);
            return ResponseEntity.ok(Map.of("message", "Assessment deleted successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage() != null && !e.getMessage().isBlank()
                            ? e.getMessage()
                            : "Failed to delete assessment."));
        }
    }

    @PutMapping("/assessments/{id}/lock")
    public ResponseEntity<?> toggleLock(@PathVariable Long id,
            @RequestParam boolean lock,
            HttpSession session) {
        ResponseEntity<Map<String, String>> forbidden = requireAdmin(session);
        if (forbidden != null)
            return forbidden;

        try {
            assessmentService.toggleLock(id, lock);
            return ResponseEntity.ok(Map.of("message", "Assessment " + (lock ? "locked" : "unlocked") + " successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to update assessment status."));
        }
    }
}
