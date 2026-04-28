package com.virtuehire.controller;

import com.virtuehire.model.AssessmentResult;
import com.virtuehire.model.Candidate;
import com.virtuehire.service.AssessmentResultService;
import com.virtuehire.service.CandidateService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/candidates")
public class CandidateRestController {

    private static final Logger logger = LoggerFactory.getLogger(CandidateRestController.class);

    private final CandidateService candidateService;
    private final AssessmentResultService assessmentResultService;
    private final Path uploadDir;

    public CandidateRestController(CandidateService candidateService,
            AssessmentResultService assessmentResultService,
            @Value("${file.upload-dir}") String uploadDirPath) {
        this.candidateService = candidateService;
        this.assessmentResultService = assessmentResultService;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    // ---------------------------
    // Candidate Registration
    // ---------------------------
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> register(@ModelAttribute Candidate candidate,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            @RequestParam(value = "profilePicFile", required = false) MultipartFile profilePicFile) throws IOException {

        if (candidate.getEmail() != null) {
            candidate.setEmail(candidate.getEmail().trim().toLowerCase(Locale.ROOT));
        }

        if (candidate.getPassword() == null || !candidate.getPassword().equals(candidate.getConfirmPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Passwords do not match"));
        }

        // Check if email already exists
        if (candidateService.findByEmail(candidate.getEmail()) != null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email already registered. Please login or use a different email."));
        }

        if (!Files.exists(uploadDir))
            Files.createDirectories(uploadDir);

        if (resumeFile != null && !resumeFile.isEmpty()) {
            String resumeFileName = System.currentTimeMillis() + "_" + resumeFile.getOriginalFilename();
            Path resumePath = uploadDir.resolve(resumeFileName);
            resumeFile.transferTo(resumePath.toFile());
            candidate.setResumePath(resumeFileName);
        }

        if (profilePicFile != null && !profilePicFile.isEmpty()) {
            String profileFileName = System.currentTimeMillis() + "_" + profilePicFile.getOriginalFilename();
            Path profilePath = uploadDir.resolve(profileFileName);
            profilePicFile.transferTo(profilePath.toFile());
            candidate.setProfilePic(profileFileName);
        }

        candidateService.save(candidate);

        String message = "Candidate registered successfully!";
        try {
            candidateService.sendVerificationMail(candidate);
            message += " Please check your email for the OTP.";
        } catch (Exception ex) {
            logger.error("Candidate registered but verification email failed for {}", candidate.getEmail(), ex);
            message += " We could not send the verification email right now. Please try again later.";
        }

        return ResponseEntity.ok(Map.of(
                "message", message,
                "requiresOtpVerification", true,
                "candidate", toCandidateResponse(candidate)));
    }

    // ---------------------------
    // Serve uploaded files
    // ---------------------------
    @GetMapping("/file/{filename}")
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws IOException {
        Path path = uploadDir.resolve(filename).normalize();
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists())
            return ResponseEntity.notFound().build();
        String contentType = Files.probeContentType(path);
        if (contentType == null)
            contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(resource);
    }

    // ---------------------------
    // Candidate Login
    // ---------------------------
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String email,
            @RequestParam String password,
            HttpSession session) {

        Candidate candidate = candidateService.login(email, password);
        if (candidate != null) {
            candidate = candidateService.refreshAssessmentAssignment(candidate);

            if (!candidateService.isEmailVerified(candidate)) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "Please verify your email using OTP",
                        "emailVerified", false));
            }

            session.setAttribute("candidate", candidate);

            // Load assessment results by subject
            List<AssessmentResult> results = assessmentResultService.getCandidateResults(candidate.getId());

            Map<String, Map<Integer, Boolean>> subjectLevelResults = new HashMap<>();
            Map<String, List<Integer>> subjectAttemptedLevels = new HashMap<>();

            // Process results per subject
            for (AssessmentResult r : results) {
                subjectLevelResults.putIfAbsent(r.getSubject(), new HashMap<>());
                subjectLevelResults.get(r.getSubject()).put(r.getLevel(), r.getScore() >= 50);

                subjectAttemptedLevels.putIfAbsent(r.getSubject(), new ArrayList<>());
                subjectAttemptedLevels.get(r.getSubject()).add(r.getLevel());
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "candidate", toCandidateResponse(candidate),
                    "results", results,
                    "subjectLevelResults", subjectLevelResults,
                    "subjectAttemptedLevels", subjectAttemptedLevels));
        } else {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(HttpSession session) {
        Candidate sessionCandidate = (Candidate) session.getAttribute("candidate");
        if (sessionCandidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        Candidate candidate = candidateService.findById(sessionCandidate.getId()).orElse(null);
        if (candidate == null) {
            session.invalidate();
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        }

        candidate = candidateService.refreshAssessmentAssignment(candidate);
        session.setAttribute("candidate", candidate);
        return ResponseEntity.ok(Map.of("candidate", toCandidateResponse(candidate)));
    }

    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProfile(@ModelAttribute Candidate payload,
            @RequestParam(value = "resumeFile", required = false) MultipartFile resumeFile,
            @RequestParam(value = "profilePicFile", required = false) MultipartFile profilePicFile,
            HttpSession session) throws IOException {

        Candidate sessionCandidate = (Candidate) session.getAttribute("candidate");
        if (sessionCandidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        Candidate candidate = candidateService.findById(sessionCandidate.getId()).orElse(null);
        if (candidate == null) {
            session.invalidate();
            return ResponseEntity.status(404).body(Map.of("error", "Candidate not found"));
        }

        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }

        candidate.setFullName(payload.getFullName());

        Candidate existingByEmail = candidateService.findByEmail(payload.getEmail());
        if (existingByEmail != null && !existingByEmail.getId().equals(candidate.getId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email already registered. Please use a different email."));
        }

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
        candidate.setSkills(payload.getSkills());

        if (resumeFile != null && !resumeFile.isEmpty()) {
            String resumeFileName = System.currentTimeMillis() + "_" + resumeFile.getOriginalFilename();
            Path resumePath = uploadDir.resolve(resumeFileName);
            resumeFile.transferTo(resumePath.toFile());
            candidate.setResumePath(resumeFileName);
        }

        if (profilePicFile != null && !profilePicFile.isEmpty()) {
            String profileFileName = System.currentTimeMillis() + "_" + profilePicFile.getOriginalFilename();
            Path profilePath = uploadDir.resolve(profileFileName);
            profilePicFile.transferTo(profilePath.toFile());
            candidate.setProfilePic(profileFileName);
        }

        Candidate updatedCandidate = candidateService.save(candidate);
        session.setAttribute("candidate", updatedCandidate);

        return ResponseEntity.ok(Map.of(
                "message", "Profile updated successfully",
                "candidate", toCandidateResponse(updatedCandidate)));
    }

    // ---------------------------
    // Recommended & All Courses
    // ---------------------------
    @GetMapping("/recommended-courses")
    public ResponseEntity<?> getRecommendedCourses(HttpSession session) {
        Candidate candidate = (Candidate) session.getAttribute("candidate");
        if (candidate == null)
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));

        String skillsStr = candidate.getSkills(); // e.g., "C,Java"
        List<String> recommendedCourses = new ArrayList<>();
        if (skillsStr != null && !skillsStr.isBlank()) {
            recommendedCourses = Arrays.asList(skillsStr.split(","));
        }

        List<String> allCourses = Arrays.asList("C", "C++", "Java", "Python", "SQL", "JavaScript", "React", "Node.js");

        return ResponseEntity.ok(Map.of(
                "recommended", recommendedCourses,
                "allCourses", allCourses));
    }

    // ---------------------------
    // Logout
    // ---------------------------
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // ---------------------------
    // Forgot Password
    // ---------------------------
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        candidateService.sendResetMail(email);
        return ResponseEntity.ok(Map.of("message", "Reset email sent successfully!"));
    }

    // ---------------------------
    // Reset Password
    // ---------------------------
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        String newPassword = request.get("newPassword");
        candidateService.resetPassword(email, code, newPassword);
        return ResponseEntity.ok(Map.of("message", "Password reset successful!"));
    }

    // ---------------------------
    // Verify Email
    // ---------------------------
    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and OTP are required"));
        }

        try {
            boolean verified = candidateService.verifyOtp(email.trim(), code.trim());
            if (verified) {
                Candidate candidate = candidateService.findByEmail(email.trim());
                return ResponseEntity.ok(Map.of(
                        "message", "Email verified successfully! You can now log in.",
                        "emailVerified", true,
                        "candidate", candidate != null ? toCandidateResponse(candidate) : null));
            } else {
                return ResponseEntity.status(400).body(Map.of("error", "Invalid or expired OTP"));
            }
        } catch (RuntimeException ex) {
            return ResponseEntity.status(400).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> request) {
        return verifyOtp(request);
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
        }

        try {
            candidateService.resendVerificationMail(email.trim());
            return ResponseEntity.ok(Map.of("message", "A new OTP has been sent to your email."));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(400).body(Map.of("error", ex.getMessage()));
        }
    }

    // ---------------------------
    // Get Candidate Results by Subject
    // ---------------------------
    @GetMapping("/results/{subject}")
    public ResponseEntity<?> getCandidateResultsBySubject(@PathVariable String subject, HttpSession session) {
        Candidate candidate = (Candidate) session.getAttribute("candidate");
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        try {
            List<AssessmentResult> results = assessmentResultService.getCandidateResults(candidate.getId(), subject);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch results for subject"));
        }
    }

    // ---------------------------
    // Get All Candidate Results
    // ---------------------------
    @GetMapping("/results")
    public ResponseEntity<?> getCandidateResults(HttpSession session) {
        Candidate candidate = (Candidate) session.getAttribute("candidate");
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        try {
            List<AssessmentResult> results = assessmentResultService.getCandidateResults(candidate.getId());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch results"));
        }
    }

    // ---------------------------
    // NEW: Get Cumulative Results with Badges
    // ---------------------------
    @GetMapping("/{id}/cumulative-results")
    public ResponseEntity<?> getCumulativeResults(@PathVariable Long id) {
        try {
            List<Map<String, Object>> results = assessmentResultService.getCandidateCumulativeResults(id);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch cumulative results"));
        }
    }

    private Map<String, Object> toCandidateResponse(Candidate candidate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", candidate.getId());
        data.put("fullName", candidate.getFullName());
        data.put("email", candidate.getEmail());
        data.put("phoneNumber", candidate.getPhoneNumber());
        data.put("alternatePhoneNumber", candidate.getAlternatePhoneNumber());
        data.put("gender", candidate.getGender());
        data.put("dateOfBirth", candidate.getDateOfBirth());
        data.put("city", candidate.getCity());
        data.put("state", candidate.getState());
        data.put("highestEducation", candidate.getHighestEducation());
        data.put("collegeUniversity", candidate.getCollegeUniversity());
        data.put("yearOfGraduation", candidate.getYearOfGraduation());
        data.put("experience", candidate.getExperience());
        data.put("experienceLevel", candidate.getExperienceLevel());
        data.put("skills", candidate.getSkills());
        data.put("resumePath", candidate.getResumePath());
        data.put("profilePic", candidate.getProfilePic());
        data.put("badge", candidate.getBadge());
        data.put("score", candidate.getScore());
        data.put("approved", candidate.getApproved());
        data.put("emailVerified", candidate.getEmailVerified());
        data.put("rejectionReason", candidate.getRejectionReason());
        data.put("assessmentTaken", candidate.getAssessmentTaken());
        data.put("assignedAssessmentName", candidate.getAssignedAssessmentName());
        data.put("assessmentAssignmentStatus", candidate.getAssessmentAssignmentStatus());
        data.put("assessmentAssignmentMessage", candidate.getAssessmentAssignmentMessage());
        return data;
    }
}
