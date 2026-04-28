package com.virtuehire.service;

import com.virtuehire.model.Candidate;
import com.virtuehire.model.Payment;
import com.virtuehire.repository.AssessmentResultRepository;
import com.virtuehire.repository.CandidateRepository;
import com.virtuehire.repository.ExamActivityRepository;
import com.virtuehire.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CandidateService {

    private final CandidateRepository repo;
    private final AssessmentResultRepository assessmentResultRepository;
    private final PaymentRepository paymentRepository;
    private final ExamActivityRepository examActivityRepository;
    private final AssessmentService assessmentService;
    private final AdminNotificationService adminNotificationService;
    private final ResumeService resumeService;
    private final Path uploadDir;

    @Autowired
    private JavaMailSender mailSender;

    private Map<String, String> resetCodes = new HashMap<>();

    public CandidateService(
            CandidateRepository repo,
            AssessmentResultRepository assessmentResultRepository,
            PaymentRepository paymentRepository,
            ExamActivityRepository examActivityRepository,
            AssessmentService assessmentService,
            AdminNotificationService adminNotificationService,
            ResumeService resumeService,
            @Value("${file.upload-dir}") String uploadDirPath) {
        this.repo = repo;
        this.assessmentResultRepository = assessmentResultRepository;
        this.paymentRepository = paymentRepository;
        this.examActivityRepository = examActivityRepository;
        this.assessmentService = assessmentService;
        this.adminNotificationService = adminNotificationService;
        this.resumeService = resumeService;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
    }

    public Candidate save(Candidate c) {
        if (c.getEmail() != null) {
            c.setEmail(normalizeEmail(c.getEmail()));
        }
        Candidate savedCandidate = repo.save(c);
        applyAssessmentAssignment(savedCandidate);
        return repo.save(savedCandidate);
    }

    public Candidate refreshAssessmentAssignment(Candidate candidate) {
        if (candidate == null) {
            return null;
        }

        applyAssessmentAssignment(candidate);
        return repo.save(candidate);
    }

    public List<Candidate> findAll() {
        return repo.findAll();
    }

    public Optional<Candidate> findById(Long id) {
        return repo.findById(id);
    }

    public Candidate findByEmail(String email) {
        if (email == null) {
            return null;
        }
        return repo.findByEmail(normalizeEmail(email)).orElse(null);
    }

    // ------------------- LOGIN -------------------
    public Candidate login(String email, String password) {
        if (email == null || password == null)
            return null;

        return repo.login(normalizeEmail(email), password.trim())
                .orElse(null);
    }

    public boolean isEmailVerified(Candidate candidate) {
        return candidate != null && Boolean.TRUE.equals(candidate.getEmailVerified());
    }

    // ------------------- RESET PASSWORD -------------------
    public void sendResetMail(String email) {
        String normalizedEmail = normalizeEmail(email);
        Candidate candidate = repo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Email not found"));

        String code = String.valueOf(new Random().nextInt(900000) + 100000); // 6 digit numeric code
        resetCodes.put(normalizedEmail, code);

        sendEmail(normalizedEmail, "VirtueHire Password Reset",
                "Hello " + candidate.getFullName() + ",\n\n"
                        + "Use this code to reset your password: " + code + "\n\n"
                        + "Thank you,\nVirtueHire Team");
    }

    public void sendVerificationMail(Candidate candidate) {
        String code = generateOtp();
        candidate.setVerificationCode(code);
        candidate.setVerificationCodeExpiry(LocalDateTime.now().plusMinutes(5));
        candidate.setEmailVerified(false);
        candidate.setApproved(false);
        repo.save(candidate);

        sendEmail(candidate.getEmail(), "VirtueHire Email Verification",
                "Hello " + candidate.getFullName() + ",\n\n"
                        + "Thank you for registering. Please use the following OTP to verify your email address:\n\n"
                        + "OTP: " + code + "\n\n"
                        + "This OTP will expire in 5 minutes.\n\n"
                        + "Thank you,\nVirtueHire Team");
    }

    public void resendVerificationMail(String email) {
        Candidate candidate = repo.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        if (Boolean.TRUE.equals(candidate.getEmailVerified())) {
            throw new RuntimeException("Email is already verified");
        }

        sendVerificationMail(candidate);
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    public void resetPassword(String email, String code, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedCode = normalizeOtp(code);

        if (!resetCodes.containsKey(normalizedEmail) || !resetCodes.get(normalizedEmail).equals(normalizedCode)) {
            throw new RuntimeException("Invalid or expired reset code");
        }

        Candidate candidate = repo.findByEmail(normalizedEmail)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        candidate.setPassword(newPassword);
        repo.save(candidate);

        resetCodes.remove(normalizedEmail);
    }

    public boolean verifyOtp(String email, String code) {
        Candidate candidate = repo.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        String normalizedCode = normalizeOtp(code);

        if (Boolean.TRUE.equals(candidate.getEmailVerified())) {
            throw new RuntimeException("Email is already verified");
        }

        if (candidate.getVerificationCode() == null || candidate.getVerificationCodeExpiry() == null) {
            return false;
        }

        if (candidate.getVerificationCodeExpiry().isBefore(LocalDateTime.now())) {
            candidate.setVerificationCode(null);
            candidate.setVerificationCodeExpiry(null);
            repo.save(candidate);
            return false;
        }

        if (!candidate.getVerificationCode().equals(normalizedCode)) {
            return false;
        }

        candidate.setEmailVerified(true);
        candidate.setApproved(true);
        candidate.setVerificationCode(null);
        candidate.setVerificationCodeExpiry(null);
        repo.save(candidate);
        return true;
    }

    public boolean verifyEmail(String email, String code) {
        return verifyOtp(email, code);
    }

    private String generateOtp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    @Transactional
    public void deleteCandidateById(Long candidateId) {
        Candidate candidate = repo.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        assessmentResultRepository.deleteByCandidateId(candidateId);
        examActivityRepository.deleteByCandidateId(candidateId);

        List<Payment> payments = paymentRepository.findByCandidateId(candidateId);
        for (Payment payment : payments) {
            payment.setCandidate(null);
        }
        paymentRepository.saveAll(payments);

        deleteUploadedFile(candidate.getResumePath());
        deleteUploadedFile(candidate.getProfilePic());
        resumeService.deleteCandidateResumes(candidateId);

        repo.delete(candidate);
    }

    private void deleteUploadedFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }

        try {
            Files.deleteIfExists(uploadDir.resolve(fileName).normalize());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to delete uploaded file: " + fileName, ex);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOtp(String code) {
        return code == null ? "" : code.trim();
    }

    private void applyAssessmentAssignment(Candidate candidate) {
        List<String> displaySkills = extractDisplaySkills(candidate.getSkills());
        String skillSignature = assessmentService.buildSkillSignature(displaySkills);

        if (displaySkills.isEmpty()) {
            candidate.setAssignedAssessmentName(null);
            candidate.setAssessmentAssignmentStatus("NO_SKILLS_SELECTED");
            candidate.setAssessmentAssignmentMessage("No skills selected yet. Add skills to get a relevant assessment.");
            return;
        }

        if (displaySkills.size() == 1) {
            assessmentService.findOrCreateSingleSkillAssessment(displaySkills.get(0))
                    .ifPresentOrElse(assessment -> {
                        candidate.setAssignedAssessmentName(assessment.getAssessmentName());
                        candidate.setAssessmentAssignmentStatus("ASSIGNED");
                        candidate.setAssessmentAssignmentMessage(
                                "Assessment assigned for skill: " + displaySkills.get(0) + ".");
                    }, () -> {
                        candidate.setAssignedAssessmentName(null);
                        candidate.setAssessmentAssignmentStatus("NO_RELEVANT_ASSESSMENT");
                        candidate.setAssessmentAssignmentMessage(
                                "No question bank is available yet for skill: " + displaySkills.get(0) + ".");
                    });
            return;
        }

        assessmentService.findAssessmentBySkillSet(displaySkills)
                .ifPresentOrElse(assessment -> {
                    candidate.setAssignedAssessmentName(assessment.getAssessmentName());
                    candidate.setAssessmentAssignmentStatus("ASSIGNED");
                    candidate.setAssessmentAssignmentMessage(
                            "Combined assessment assigned for skills: " + String.join(", ", displaySkills) + ".");
                }, () -> {
                    candidate.setAssignedAssessmentName(null);
                    candidate.setAssessmentAssignmentStatus("PENDING_COMBINED_ASSESSMENT");
                    candidate.setAssessmentAssignmentMessage(
                            "A candidate has registered with skills: " + String.join(", ", displaySkills)
                                    + ". A combined assessment needs to be created.");
                    adminNotificationService.createCombinedAssessmentNotification(candidate, displaySkills, skillSignature);
                });
    }

    private List<String> extractDisplaySkills(String skills) {
        if (skills == null || skills.isBlank()) {
            return List.of();
        }

        Map<String, String> uniqueSkills = new LinkedHashMap<>();
        for (String rawSkill : skills.split(",")) {
            if (rawSkill == null) {
                continue;
            }

            String trimmed = rawSkill.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            String key = trimmed.toLowerCase(Locale.ROOT);
            uniqueSkills.putIfAbsent(key, trimmed);
        }

        return new ArrayList<>(uniqueSkills.values());
    }

    // // ------------------- OTHER METHODS -------------------
    // public List<Candidate> findByApprovedFalse() {
    // return repo.findAll().stream()
    // .filter(c -> Boolean.FALSE.equals(c.getApproved()))
    // .collect(Collectors.toList());
    // }
    //
    // public List<Candidate> searchCandidates(String name, String city, Integer
    // year) {
    // return repo.findAll().stream()
    // .filter(c -> (name == null ||
    // c.getFullName().toLowerCase().contains(name.toLowerCase())) &&
    // (city == null || (c.getCity() != null && c.getCity().equalsIgnoreCase(city)))
    // &&
    // (year == null || (c.getYearOfGraduation() != null &&
    // c.getYearOfGraduation().equals(year))))
    // .collect(Collectors.toList());
    // }
    // }
    // ------------------- FIND UNAPPROVED -------------------
    public List<Candidate> findByApprovedFalse() {
        return repo.findAll().stream()
                .filter(c -> Boolean.FALSE.equals(c.getApproved()))
                .collect(Collectors.toList());
    }

    // ------------------- SEARCH BY NAME, CITY, YEAR -------------------
    public List<Candidate> searchCandidates(String name, String city, Integer year) {
        return repo.findAll().stream()
                .filter(c -> (name == null || c.getFullName().toLowerCase().contains(name.toLowerCase())) &&
                        (city == null || (c.getCity() != null && c.getCity().equalsIgnoreCase(city))) &&
                        (year == null || (c.getYearOfGraduation() != null && c.getYearOfGraduation().equals(year))))
                .collect(Collectors.toList());
    }

    // ------------------- SEARCH CANDIDATES FOR HR FILTER -------------------
    public List<Candidate> searchCandidatesForHr(String skills, String experienceLevel, Integer minScore) {
        return repo.findAll().stream()
                .filter(c -> (skills == null || (c.getSkills() != null &&
                        c.getSkills().toLowerCase().contains(skills.toLowerCase()))))
                .filter(c -> (experienceLevel == null || (c.getExperienceLevel() != null &&
                        c.getExperienceLevel().equalsIgnoreCase(experienceLevel))))
                .filter(c -> (minScore == null || (c.getScore() != null &&
                        c.getScore() >= minScore)))
                .collect(Collectors.toList());
    }

    public List<Candidate> searchCandidatesForHrDashboard(
            String name,
            String skill,
            String experienceFilter,
            String scoreSort) {

        Comparator<Candidate> scoreComparator = Comparator.comparing(
                candidate -> candidate.getScore() != null ? candidate.getScore() : 0);

        return repo.findAll().stream()
                .filter(candidate -> {
                    if (name == null || name.isBlank()) {
                        return true;
                    }
                    return candidate.getFullName() != null
                            && candidate.getFullName().toLowerCase().contains(name.trim().toLowerCase());
                })
                .filter(candidate -> {
                    if (skill == null || skill.isBlank()) {
                        return true;
                    }
                    return candidate.getSkills() != null
                            && candidate.getSkills().toLowerCase().contains(skill.trim().toLowerCase());
                })
                .filter(candidate -> {
                    if (experienceFilter == null || experienceFilter.isBlank() || "all".equalsIgnoreCase(experienceFilter)) {
                        return true;
                    }

                    int experience = candidate.getExperience() != null ? candidate.getExperience() : 0;
                    return switch (experienceFilter.toLowerCase()) {
                        case "0-1" -> experience <= 1;
                        case "1" -> experience == 1;
                        case "2+" -> experience >= 2;
                        case "3+" -> experience >= 3;
                        default -> true;
                    };
                })
                .sorted((first, second) -> {
                    if ("asc".equalsIgnoreCase(scoreSort)) {
                        return scoreComparator.compare(first, second);
                    }
                    if ("desc".equalsIgnoreCase(scoreSort)) {
                        return scoreComparator.reversed().compare(first, second);
                    }
                    return 0;
                })
                .collect(Collectors.toList());
    }

}
