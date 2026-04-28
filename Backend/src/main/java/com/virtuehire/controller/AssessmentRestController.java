package com.virtuehire.controller;

import com.virtuehire.model.*;
import com.virtuehire.service.*;
import com.virtuehire.repository.*;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

import java.util.*;

@RestController
@RequestMapping("/api/assessment")
@CrossOrigin(origins = { "https://admin.virtuehire.in", "https://virtuehire.in", "http://localhost:3000" },
             allowCredentials = "true")
public class AssessmentRestController {

    private final AssessmentResultService resultService;
    private final AssessmentService assessmentService;
    private final AssessmentQuestionRepository aqRepo;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final QuestionRepository questionRepository;
    private final QuestionService questionService;
    private final CodeExecutionService codeExecutionService;

    public AssessmentRestController(
            AssessmentResultService resultService,
            AssessmentService assessmentService,
            AssessmentQuestionRepository aqRepo,
            CandidateAnswerRepository candidateAnswerRepository,
            QuestionRepository questionRepository,
            QuestionService questionService,
            CodeExecutionService codeExecutionService) {

        this.resultService = resultService;
        this.assessmentService = assessmentService;
        this.aqRepo = aqRepo;
        this.candidateAnswerRepository = candidateAnswerRepository;
        this.questionRepository = questionRepository;
        this.questionService = questionService;
        this.codeExecutionService = codeExecutionService;
    }

    // =========================================================
    // ✅ FIX 1: STATUS ENDPOINT — was completely missing
    // Frontend calls: GET /assessment/status/{subject}
    // (via axios baseURL = /api, so full path = /api/assessment/status/{subject})
    // =========================================================

    @GetMapping("/status/{assessmentName}")
    public ResponseEntity<?> getAssessmentStatus(
            @PathVariable String assessmentName,
            HttpSession session) {

        Candidate candidate = (Candidate) session.getAttribute("candidate");
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        Optional<Assessment> assessmentOpt =
                assessmentService.getAssessmentByName(assessmentName);

        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Assessment not found: " + assessmentName));
        }

        // Determine which level the candidate can attempt next
        // Check completed results to find the next unlocked level
        int nextLevel = resultService.getNextLevel(candidate.getId(), assessmentOpt.get().getId());

        return ResponseEntity.ok(Map.of(
                "assessmentName", assessmentName,
                "nextLevel", nextLevel
        ));
    }

    // =========================================================
    // ✅ FIX 2: FILE UPLOAD — now routes PDF correctly
    // Removed call to deleted saveCodingFromPDF(),
    // now uses saveQuestionsFromUpload() with required params
    // =========================================================

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String testName,
            @RequestParam(required = false) String input1,
            @RequestParam(required = false) String output1,
            @RequestParam(required = false) String input2,
            @RequestParam(required = false) String output2) {

        try {
            String fileName = file.getOriginalFilename();

            if (fileName == null) {
                return ResponseEntity.badRequest().body("Invalid file");
            }

            System.out.println("Uploading file: " + fileName);

            // ✅ Single entry point handles both CSV and PDF correctly
            questionService.saveQuestionsFromUpload(file, testName, input1, output1, input2, output2);

            return ResponseEntity.ok("Upload successful");

        } catch (IllegalArgumentException e) {
            // Validation errors (missing fields, bad format, etc.)
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body("Upload failed: " + e.getMessage());
        }
    }

    // =========================================================
    // GET QUESTIONS FOR A LEVEL
    // =========================================================

    @GetMapping("/{assessmentName}/level/{level}")
    public ResponseEntity<?> getLevelQuestions(
            @PathVariable String assessmentName,
            @PathVariable int level,
            HttpSession session) {

        Candidate candidate = (Candidate) session.getAttribute("candidate");
        if (candidate == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
        }

        Optional<Assessment> assessmentOpt =
                assessmentService.getAssessmentByName(assessmentName);

        if (assessmentOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Assessment not found: " + assessmentName));
        }

        Assessment assessment = assessmentOpt.get();

        List<AssessmentSection> sections =
                assessmentService.getAssessmentSections(assessment.getId());

        if (sections == null || sections.size() < level) {
            return ResponseEntity.status(404).body(Map.of("error", "Section not found for level " + level));
        }

        AssessmentSection section = sections.get(level - 1);

        List<Question> questions =
                aqRepo.findQuestionsBySectionId(section.getId());

        if (questions == null || questions.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "No questions found for this level.",
                    "questions", List.of()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "subject", assessmentName,
                "level", level,
                "sectionName", section.getSubject(),
                "timeLimit", section.getSectionTime(),
                "sectionMode", section.getSectionMode(),
                "supportedLanguages", parseSupportedLanguages(section.getSupportedLanguages()),
                "questions", questions
        ));
    }

    // =========================================================
    // SUBMIT ANSWERS
    // =========================================================

    @PostMapping("/{assessmentName}/submit/{level}")
    public ResponseEntity<?> submitAnswers(
            @PathVariable String assessmentName,
            @PathVariable int level,
            @RequestBody SubmissionRequest request,
            HttpSession session) {

        try {
            Candidate candidate = (Candidate) session.getAttribute("candidate");
            if (candidate == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Not logged in"));
            }

            Map<String, CodingAnswerRequest> codingAnswers =
                    request.codingAnswers != null ? request.codingAnswers : new HashMap<>();

            Optional<Assessment> assessmentOpt =
                    assessmentService.getAssessmentByName(assessmentName);

            if (assessmentOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Assessment not found"));
            }

            Assessment assessment = assessmentOpt.get();

            List<AssessmentSection> sections =
                    assessmentService.getAssessmentSections(assessment.getId());

            if (sections.size() < level) {
                return ResponseEntity.status(404).body(Map.of("error", "Section not found"));
            }

            AssessmentSection section = sections.get(level - 1);
            List<AssessmentQuestion> aqs = aqRepo.findBySectionId(section.getId());

            int correct = 0;

            for (AssessmentQuestion aq : aqs) {
                Question q = aq.getQuestion();

                if (q.isHasCompiler()) {
                    CodingAnswerRequest ans = codingAnswers.get(q.getId().toString());

                    if (ans != null && ans.sourceCode != null) {
                        List<TestCase> testCases = questionService.getTestCases(q.getId());

                        Map<String, Object> result = codeExecutionService.submit(
                                ans.sourceCode,
                                ans.languageId,
                                testCases);

                        int passed = ((Number) result.get("passedTestCases")).intValue();
                        int total  = ((Number) result.get("totalTestCases")).intValue();

                        if (passed == total) correct++;
                    }
                } else {
                    // MCQ scoring
                    String given = request.answers != null
                            ? request.answers.get(q.getId().toString())
                            : null;
                    if (given != null && given.equalsIgnoreCase(q.getCorrectAnswer())) {
                        correct++;
                    }
                }
            }

            int total = aqs.size();
            int percentage = total > 0 ? (correct * 100 / total) : 0;
            boolean passed = percentage >= 60;

            return ResponseEntity.ok(Map.of(
                    "score", correct,
                    "percentage", percentage,
                    "passed", passed
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal Server Error: " + e.getMessage()));
        }
    }

    // =========================================================
    // SUBJECTS
    // =========================================================

    @GetMapping("/subjects")
    public List<String> getConfiguredSubjects() {
        return assessmentService.getAllAssessmentNames();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private List<String> parseSupportedLanguages(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    // =========================================================
    // DTOs
    // =========================================================

    public static class SubmissionRequest {
        public Map<String, String> answers;
        public Map<String, CodingAnswerRequest> codingAnswers;
        public Integer violations;
        public String lastActivity;
        public Boolean isAutoSubmit;
    }

    public static class CodingAnswerRequest {
        public String sourceCode;
        public Integer languageId;
        public Boolean submitted;
    }
}