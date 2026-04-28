package com.virtuehire.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuehire.model.TestCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Wraps the Judge0 REST API for code execution.
 *
 * Add to application.properties:
 *   judge0.base-url=https://judge0-ce.p.rapidapi.com
 *   judge0.api-key=YOUR_KEY_HERE
 *
 * Get a free key at: https://rapidapi.com/judge0-official/api/judge0-ce
 */
@Service
public class CodeExecutionService {

    @Value("${judge0.base-url}")
    private String baseUrl;

    @Value("${judge0.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Common language IDs ──────────────────────────────────────────────────
    // 71 = Python 3 | 62 = Java | 54 = C++ | 63 = JavaScript | 50 = C

    /**
     * RUN — execute code against custom stdin, return raw output.
     * Used by the "Run" button. Not graded.
     */
    public Map<String, Object> run(String sourceCode, int languageId, String stdin) {
        String token  = submitToJudge0(sourceCode, languageId, stdin, null);
        JsonNode result = pollResult(token);
        return buildRunResponse(result);
    }

    /**
     * SUBMIT — run against stored test cases and compare outputs.
     * Used by the "Submit" button. Returns pass/fail per test case.
     */
    public Map<String, Object> submit(String sourceCode, int languageId, List<TestCase> testCases) {
        List<Map<String, Object>> results = new ArrayList<>();
        int passed = 0;

        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc     = testCases.get(i);
            String   token  = submitToJudge0(sourceCode, languageId,
                                              tc.getInput(), tc.getExpectedOutput());
            JsonNode result  = pollResult(token);

            String  actual  = decode(result, "stdout");
            String  status  = result.path("status").path("description").asText();
            boolean ok      = normalize(actual).equals(normalize(tc.getExpectedOutput()));

            if (ok) passed++;

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("index",           i + 1);
            r.put("input",           tc.getInput());
            r.put("expectedOutput",  tc.getExpectedOutput());
            r.put("actualOutput",    actual);
            r.put("passed",          ok);
            r.put("status",          status);
            results.add(r);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalTestCases",  testCases.size());
        response.put("passedTestCases", passed);
        response.put("results",         results);
        return response;
    }

    // ── Judge0 internals ─────────────────────────────────────────────────────

    private String submitToJudge0(String sourceCode, int languageId,
                                   String stdin, String expectedOutput) {
        Map<String, Object> body = new HashMap<>();
        body.put("language_id", languageId);
        body.put("source_code", Base64.getEncoder()
                .encodeToString(sourceCode.getBytes()));
        body.put("stdin", stdin != null
                ? Base64.getEncoder().encodeToString(stdin.getBytes()) : "");
        if (expectedOutput != null) {
            body.put("expected_output", Base64.getEncoder()
                    .encodeToString(expectedOutput.getBytes()));
        }

        ResponseEntity<JsonNode> resp = restTemplate.exchange(
                baseUrl + "/submissions?base64_encoded=true&wait=false",
                HttpMethod.POST,
                new HttpEntity<>(body, headers()),
                JsonNode.class);

        return Objects.requireNonNull(resp.getBody()).path("token").asText();
    }

    private JsonNode pollResult(String token) {
        String url = baseUrl + "/submissions/" + token + "?base64_encoded=true";
        for (int i = 0; i < 20; i++) {
            try { Thread.sleep(600); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ResponseEntity<JsonNode> resp = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers()), JsonNode.class);
            JsonNode body = Objects.requireNonNull(resp.getBody());
            if (body.path("status").path("id").asInt() > 2) return body;
        }
        throw new RuntimeException("Judge0 timed out for token: " + token);
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            h.set("X-RapidAPI-Key",  apiKey);
            h.set("X-RapidAPI-Host", "judge0-ce.p.rapidapi.com");
        }
        return h;
    }

    private Map<String, Object> buildRunResponse(JsonNode r) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("stdout",            decode(r, "stdout"));
        res.put("stderr",            decode(r, "stderr"));
        res.put("compileOutput",     decode(r, "compile_output"));
        res.put("statusDescription", r.path("status").path("description").asText());
        res.put("success",           "Accepted".equalsIgnoreCase(
                                         r.path("status").path("description").asText()));
        return res;
    }

    /** Decodes a base64 field from Judge0 response. Returns "" if absent. */
    private String decode(JsonNode node, String field) {
        String raw = node.path(field).asText("");
        if (raw.isBlank()) return "";
        try {
            return new String(Base64.getDecoder().decode(raw));
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /** Trim and normalise line endings for output comparison. */
    private String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").stripTrailing().trim();
    }
}