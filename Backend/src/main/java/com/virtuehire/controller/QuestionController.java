package com.virtuehire.controller;

import com.virtuehire.model.*;
import com.virtuehire.repository.QuestionRepository;
import com.virtuehire.service.CodeExecutionService;
import com.virtuehire.service.QuestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Original QuestionController — all existing endpoints preserved verbatim.
 * Three new endpoints added at the bottom for coding question execution.
 */
@RestController
@RequestMapping("/api/questions")
@CrossOrigin(origins = "https://admin.virtuehire.in")
public class QuestionController {

    private final QuestionRepository  questionRepository;
    // ── NEW ──
    private final QuestionService     questionService;
    private final CodeExecutionService codeExecutionService;

    public QuestionController(QuestionRepository questionRepository,
                               QuestionService questionService,
                               CodeExecutionService codeExecutionService) {
        this.questionRepository   = questionRepository;
        this.questionService      = questionService;
        this.codeExecutionService = codeExecutionService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXISTING ENDPOINTS — completely unchanged
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    public List<Question> getAllQuestions() {
        return questionRepository.findAll();
    }

    @GetMapping("/subject/{subject}")
    public Map<String, Object> getQuestionsBySubject(@PathVariable String subject) {
        List<Question> questions = questionRepository.findBySubject(subject);
        if (questions.isEmpty()) {
            return Map.of("message", "No questions available for subject: " + subject);
        }
        return Map.of("questions", questions);
    }

    @GetMapping("/{subject}/level/{level}")
    public Map<String, Object> getQuestionsBySubjectAndLevel(@PathVariable String subject,
                                                              @PathVariable int level) {
        List<Question> questions = questionRepository.findBySubjectAndLevel(subject, level);
        if (questions.isEmpty()) {
            return Map.of("message", "No questions found for " + subject + " level " + level);
        }
        return Map.of("questions", questions);
    }

    @GetMapping("/level/{level}")
    public Map<String, Object> getQuestionsByLevel(@PathVariable int level) {
        List<Question> questions = questionRepository.findByLevel(level);
        if (questions.isEmpty()) {
            return Map.of("message", "No questions available for this level");
        }
        return Map.of("questions", questions);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Get coding detail + test cases for a question
    // GET /api/questions/{id}/coding
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}/coding")
    public ResponseEntity<?> getCodingQuestion(@PathVariable Long id) {
        Question q = questionRepository.findById(id).orElse(null);
        if (q == null) return ResponseEntity.notFound().build();
        if (!q.isHasCompiler())
            return ResponseEntity.badRequest().body(Map.of("error", "Not a coding question"));

        CodingDetail detail    = questionService.getCodingDetail(id).orElse(null);
        List<TestCase> testCases = questionService.getTestCases(id);

        return ResponseEntity.ok(Map.of(
                "questionId",  id,
                "description", detail != null ? detail.getDescription() : "",
                "testCaseCount", testCases.size()
                // Note: test case inputs/outputs are NOT exposed to the candidate
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Run code with custom stdin (the "Run" button)
    // POST /api/questions/code/run
    //
    // Request body: { "questionId": 1, "sourceCode": "...", "languageId": 71, "stdin": "1 2" }
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/code/run")
    public ResponseEntity<?> runCode(@RequestBody Map<String, Object> body) {
        try {
            String sourceCode = (String) body.get("sourceCode");
            int    languageId = (int)    body.get("languageId");
            String stdin      = (String) body.getOrDefault("stdin", "");

            if (sourceCode == null || sourceCode.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "sourceCode is required"));

            Map<String, Object> result = codeExecutionService.run(sourceCode, languageId, stdin);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // NEW: Submit code — run against stored test cases (the "Submit" button)
    // POST /api/questions/code/submit
    //
    // Request body: { "questionId": 1, "sourceCode": "...", "languageId": 71 }
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/code/submit")
    public ResponseEntity<?> submitCode(@RequestBody Map<String, Object> body) {
        try {
            Long   questionId = Long.valueOf(body.get("questionId").toString());
            String sourceCode = (String) body.get("sourceCode");
            int    languageId = (int)    body.get("languageId");

            Question q = questionRepository.findById(questionId).orElse(null);
            if (q == null)
                return ResponseEntity.notFound().build();
            if (!q.isHasCompiler())
                return ResponseEntity.badRequest().body(Map.of("error", "Not a coding question"));

            List<TestCase> testCases = questionService.getTestCases(questionId);
            if (testCases.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "No test cases found"));

            Map<String, Object> result = codeExecutionService.submit(sourceCode, languageId, testCases);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}