package com.virtuehire.service;

import com.virtuehire.model.Assessment;
import com.virtuehire.model.AssessmentQuestion;
import com.virtuehire.model.AssessmentSection;
import com.virtuehire.model.Question;
import com.virtuehire.repository.AssessmentQuestionRepository;
import com.virtuehire.repository.AssessmentRepository;
import com.virtuehire.repository.AssessmentResultRepository;
import com.virtuehire.repository.AssessmentSectionRepository;
import com.virtuehire.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepo;
    private final AssessmentSectionRepository sectionRepo;
    private final AssessmentQuestionRepository aqRepo;
    private final QuestionRepository questionRepo;
    private final AssessmentResultRepository resultRepo;
    private final AdminNotificationService adminNotificationService;

    public AssessmentService(AssessmentRepository assessmentRepo,
                             AssessmentSectionRepository sectionRepo,
                             AssessmentQuestionRepository aqRepo,
                             QuestionRepository questionRepo,
                             AssessmentResultRepository resultRepo,
                             AdminNotificationService adminNotificationService) {
        this.assessmentRepo = assessmentRepo;
        this.sectionRepo = sectionRepo;
        this.aqRepo = aqRepo;
        this.questionRepo = questionRepo;
        this.resultRepo = resultRepo;
        this.adminNotificationService = adminNotificationService;
    }

    @Transactional
    public Assessment createAssessment(String name, String description, List<Map<String, Object>> sectionsData) {
        // Validation step
        for (Map<String, Object> sec : sectionsData) {
            String subject = normalizeSkill((String) sec.get("subject"));
            int requestedCount = (Integer) sec.get("questionCount");
            String sectionMode = normalizeSectionMode((String) sec.get("sectionMode"));
            List<Question> availableQuestions = getQuestionsForMode(subject, sectionMode);

            long availableCount = availableQuestions.size();
            if (availableCount < requestedCount) {
                throw new RuntimeException("Only " + availableCount + " " + formatModeLabel(sectionMode)
                        + " questions available for " + subject + ".");
            }

            if ("COMPILER".equals(sectionMode)) {
                List<String> supportedLanguages = toStringList(sec.get("supportedLanguages"));
                if (supportedLanguages.isEmpty()) {
                    throw new RuntimeException("Select at least one compiler language for " + subject + ".");
                }
            }
        }

        // 1. Create Assessment
        Assessment assessment = new Assessment(name, description);
        assessment = assessmentRepo.save(assessment);

        // 2. Iterate sections, save sections, assign random questions
        int sectionNumber = 1;
        for (Map<String, Object> sec : sectionsData) {
            String subject = normalizeSkill((String) sec.get("subject"));
            int requestedCount = (Integer) sec.get("questionCount");
            int timeLimit = (Integer) sec.get("timeLimit");
            int passPercentage = (Integer) sec.get("passPercentage");
            String sectionMode = normalizeSectionMode((String) sec.get("sectionMode"));
            String supportedLanguages = String.join(",", toStringList(sec.get("supportedLanguages")));

            AssessmentSection section = new AssessmentSection(
                    assessment,
                    sectionNumber++,
                    subject,
                    requestedCount,
                    timeLimit,
                    passPercentage,
                    sectionMode,
                    supportedLanguages);
            section = sectionRepo.save(section);

            // Fetch and shuffle questions
            List<Question> availableQs = getQuestionsForMode(subject, sectionMode);
            Collections.shuffle(availableQs);

            List<Question> selectedQs = availableQs.subList(0, requestedCount);

            List<AssessmentQuestion> aqs = new ArrayList<>();
            for (Question q : selectedQs) {
                aqs.add(new AssessmentQuestion(assessment, section, q));
            }
            aqRepo.saveAll(aqs);
        }

        adminNotificationService.resolveCombinedAssessmentNotification(
                buildSkillSignatureFromSections(getAssessmentSections(assessment.getId())));

        return assessment;
    }

    public List<Assessment> getAllAssessments() {
        return assessmentRepo.findAll();
    }

    public Optional<Assessment> getAssessmentByName(String name) {
        return assessmentRepo.findByAssessmentName(name);
    }

    public List<String> getAllAssessmentNames() {
        return assessmentRepo.findAll().stream()
                             .map(Assessment::getAssessmentName)
                             .distinct()
                             .toList();
    }

    public Optional<Assessment> findAssessmentBySkillSet(List<String> skills) {
        String requestedSignature = buildSkillSignature(skills);
        if (requestedSignature.isBlank()) {
            return Optional.empty();
        }

        return assessmentRepo.findAll().stream()
                .sorted(Comparator.comparing(Assessment::getCreatedAt).reversed())
                .filter(assessment -> requestedSignature.equals(
                        buildSkillSignatureFromSections(getAssessmentSections(assessment.getId()))))
                .findFirst();
    }

    @Transactional
    public Optional<Assessment> findOrCreateSingleSkillAssessment(String skill) {
        if (skill == null || skill.isBlank()) {
            return Optional.empty();
        }

        Optional<Assessment> existingAssessment = findAssessmentBySkillSet(List.of(skill));
        if (existingAssessment.isPresent()) {
            return existingAssessment;
        }

        String normalizedSkill = normalizeSkill(skill);
        List<Question> questions = questionRepo.findBySubject(normalizedSkill);
        if (questions.isEmpty()) {
            return Optional.empty();
        }

        int questionCount = Math.min(questions.size(), 10);
        int timeLimit = Math.max(10, questionCount);

        // ✅ FIX: Use the skill name directly as the assessment name.
        // Previously this called buildAutoAssessmentName() which appended
        // " Assessment" — turning "Python" into "Python Assessment" and
        // creating a duplicate alongside any manually created "Python" assessment.
        String assessmentName = normalizedSkill;

        Assessment assessment = createAssessment(
                assessmentName,
                "Auto-generated single-skill assessment for " + normalizedSkill,
                List.of(Map.<String, Object>of(
                        "subject", normalizedSkill,
                        "questionCount", questionCount,
                        "timeLimit", timeLimit,
                        "passPercentage", 60)));

        return Optional.of(assessment);
    }

    public List<AssessmentSection> getAssessmentSections(Long assessmentId) {
        return sectionRepo.findByAssessmentIdOrderBySectionNumberAsc(assessmentId);
    }

    @Transactional
    public void deleteAssessment(Long id) {
        Assessment assessment = assessmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));

        List<AssessmentQuestion> aqs = aqRepo.findByAssessmentId(id);
        aqRepo.deleteAll(aqs);

        List<AssessmentSection> sections = sectionRepo.findByAssessmentIdOrderBySectionNumberAsc(id);
        sectionRepo.deleteAll(sections);

        resultRepo.deleteBySubjectIgnoreCase(assessment.getAssessmentName());
        assessmentRepo.delete(assessment);
    }

    @Transactional
    public void toggleLock(Long id, boolean lock) {
        Assessment a = assessmentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Assessment not found"));
        a.setLocked(lock);
        assessmentRepo.save(a);
    }

    public String buildSkillSignature(List<String> skills) {
        if (skills == null) {
            return "";
        }

        return skills.stream()
                .filter(skill -> skill != null && !skill.isBlank())
                .map(this::normalizeSkill)
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private String buildSkillSignatureFromSections(List<AssessmentSection> sections) {
        Set<String> skills = sections.stream()
                .map(AssessmentSection::getSubject)
                .filter(subject -> subject != null && !subject.isBlank())
                .map(this::normalizeSkill)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return buildSkillSignature(new ArrayList<>(skills));
    }

    private String normalizeSkill(String skill) {
        if (skill == null) {
            return "";
        }

        String trimmed = skill.trim();
        return questionRepo.findAll().stream()
                .map(Question::getSubject)
                .filter(subject -> subject != null
                        && canonicalizeSkill(subject).equals(canonicalizeSkill(trimmed)))
                .findFirst()
                .orElse(trimmed);
    }

    private String canonicalizeSkill(String skill) {
        if (skill == null) {
            return "";
        }

        return skill
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    // ✅ REMOVED: buildAutoAssessmentName() — was the root cause of duplicates.
    // It appended " Assessment" to every skill name, creating "Python Assessment"
    // alongside manually created "Python" assessments.

    private List<Question> getQuestionsForMode(String subject, String sectionMode) {
        if ("COMPILER".equals(sectionMode)) {
            return questionRepo.findBySubjectAndHasCompiler(subject, true);
        }
        return questionRepo.findBySubjectAndHasCompiler(subject, false);
    }

    private String normalizeSectionMode(String sectionMode) {
        if (sectionMode == null || sectionMode.isBlank()) {
            return "NO_COMPILER";
        }
        return "COMPILER".equalsIgnoreCase(sectionMode.trim()) ? "COMPILER" : "NO_COMPILER";
    }

    private String formatModeLabel(String sectionMode) {
        return "COMPILER".equals(sectionMode) ? "coding/compiler-enabled" : "no-compiler";
    }

    private List<String> toStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList)) {
            return List.of();
        }

        return rawList.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}