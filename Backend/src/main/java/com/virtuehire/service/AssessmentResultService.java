package com.virtuehire.service;

import com.virtuehire.model.AssessmentResult;
import com.virtuehire.model.Candidate;
import com.virtuehire.repository.AssessmentResultRepository;
import com.virtuehire.repository.CandidateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssessmentResultService {

    @Autowired
    private AssessmentResultRepository resultRepo;

    @Autowired
    private CandidateRepository candidateRepo;

    @Value("${assessment.pass.percent:60}")
    private double passPercentage;

    private static final int LOCK_DURATION_MINUTES = 3;

    // =========================================================
    // ✅ NEW: getNextLevel — used by AssessmentRestController
    // Returns the next level the candidate is allowed to attempt.
    // Logic: keep advancing while they have passed the previous level.
    // If they haven't passed level N, they stay on level N.
    // =========================================================

    public int getNextLevel(Long candidateId, Long assessmentId) {
        // We use subject-based results (existing pattern in this service).
        // assessmentId is not stored on results — we resolve via subject name
        // by checking all results for this candidate and finding the highest
        // passed level across any subject tied to this assessment.
        // Since results are stored by subject string, we check levels 1-3.

        List<AssessmentResult> allResults = resultRepo.findByCandidateId(candidateId);

        int nextLevel = 1;

        for (int level = 1; level <= 3; level++) {
            final int lvl = level;
            boolean passedThisLevel = allResults.stream()
                    .filter(r -> r.getLevel() == lvl)
                    .anyMatch(r -> r.getScore() >= passPercentage);

            if (passedThisLevel) {
                nextLevel = level + 1; // unlocked the next level
            } else {
                break; // stop — they haven't passed this level yet
            }
        }

        // Cap at 3 (max level)
        return Math.min(nextLevel, 3);
    }

    // =========================================================
    // EXISTING METHODS — UNCHANGED
    // =========================================================

    public AssessmentResult saveResult(Long candidateId, String subject, int level, int score, int passPercentageRequired) {
        Candidate candidate = candidateRepo.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));

        AssessmentResult result = resultRepo.findByCandidateIdAndSubjectIgnoreCaseAndLevel(candidateId, subject, level)
                .stream()
                .findFirst()
                .orElseGet(() -> new AssessmentResult(candidate, subject, level, score));

        result.setCandidate(candidate);
        result.setSubject(subject);
        result.setLevel(level);
        result.setScore(score);
        result.setAttemptedAt(LocalDateTime.now());

        if (score < passPercentageRequired) {
            result.setLockedAt(LocalDateTime.now());
        } else {
            result.setLockedAt(null);
        }

        AssessmentResult saved = resultRepo.save(result);

        candidate.setAssessmentTaken(true);
        updateCandidateBadge(candidate);
        candidateRepo.save(candidate);

        return saved;
    }

    public void updateCandidateBadge(Candidate candidate) {
        List<Map<String, Object>> cumulativeResults = getCandidateCumulativeResults(candidate.getId());

        List<String> expertBadges = cumulativeResults.stream()
                .map(res -> (String) res.get("badge"))
                .filter(b -> b != null && b.endsWith("Expert"))
                .distinct()
                .collect(Collectors.toList());

        if (expertBadges.isEmpty()) {
            candidate.setBadge("No badge");
        } else {
            candidate.setBadge(String.join(", ", expertBadges));
        }
    }

    public boolean hasAttempted(Long candidateId, String subject, int level) {
        return !resultRepo.findByCandidateIdAndSubjectIgnoreCaseAndLevel(candidateId, subject, level).isEmpty();
    }

    public boolean hasPassed(Long candidateId, String subject, int level) {
        return resultRepo.findByCandidateIdAndSubjectIgnoreCaseAndLevel(candidateId, subject, level).stream()
                .anyMatch(r -> r.getScore() >= passPercentage);
    }

    public boolean canAttemptLevel(Long candidateId, String subject, int requestedLevel) {
        if (requestedLevel == 1)
            return true;

        List<AssessmentResult> prevResults = resultRepo.findByCandidateIdAndSubjectIgnoreCaseAndLevel(candidateId,
                subject, requestedLevel - 1);

        if (prevResults.isEmpty())
            return false;

        if (prevResults.stream().anyMatch(r -> r.getScore() >= passPercentage))
            return true;

        AssessmentResult latestPrev = prevResults.stream()
                .max(Comparator.comparing(AssessmentResult::getAttemptedAt))
                .get();

        if (latestPrev.getLockedAt() != null) {
            LocalDateTime unlockTime = latestPrev.getLockedAt().plusMinutes(LOCK_DURATION_MINUTES);
            return LocalDateTime.now().isAfter(unlockTime);
        }

        return false;
    }

    public List<AssessmentResult> getResults(Long candidateId, String subject) {
        return resultRepo.findByCandidateIdAndSubjectIgnoreCase(candidateId, subject);
    }

    public List<AssessmentResult> getCandidateResults(Long candidateId) {
        return resultRepo.findByCandidateId(candidateId);
    }

    public List<AssessmentResult> getCandidateResults(Long candidateId, String subject) {
        return getResults(candidateId, subject);
    }

    public Map<Integer, String> getLevelStatus(Long candidateId, String subject) {
        Map<Integer, String> status = new HashMap<>();

        for (int level = 1; level <= 3; level++) {
            List<AssessmentResult> results = resultRepo.findByCandidateIdAndSubjectIgnoreCaseAndLevel(candidateId,
                    subject, level);

            if (results.isEmpty()) {
                if (canAttemptLevel(candidateId, subject, level)) {
                    status.put(level, "available");
                } else {
                    status.put(level, "locked");
                }
            } else {
                boolean passed = results.stream().anyMatch(r -> r.getScore() >= passPercentage);
                if (passed) {
                    status.put(level, "passed");
                } else {
                    AssessmentResult latest = results.stream()
                            .max(Comparator.comparing(AssessmentResult::getAttemptedAt))
                            .get();
                    if (latest.getLockedAt() != null
                            && LocalDateTime.now().isBefore(latest.getLockedAt().plusMinutes(LOCK_DURATION_MINUTES))) {
                        status.put(level, "locked");
                    } else {
                        status.put(level, "failed");
                    }
                }
            }
        }

        return status;
    }

    public List<Map<String, Object>> getCandidateCumulativeResults(Long candidateId) {
        List<AssessmentResult> results = resultRepo.findByCandidateId(candidateId);

        Map<String, List<AssessmentResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(AssessmentResult::getSubject));

        List<Map<String, Object>> finalResults = new ArrayList<>();

        for (String subject : grouped.keySet()) {
            List<AssessmentResult> subjectResults = grouped.get(subject);

            double totalScore = subjectResults.stream()
                    .mapToDouble(AssessmentResult::getScore)
                    .sum();
            double maxScore = subjectResults.size() * 100;
            double percentage = Math.round((totalScore / maxScore) * 100);

            String badge = percentage > 95 ? subject + " Expert" : "No Badge";

            Map<String, Object> subjectResult = new HashMap<>();
            subjectResult.put("subject", subject);
            subjectResult.put("cumulativePercentage", percentage);
            subjectResult.put("badge", badge);
            subjectResult.put("candidateId", candidateId);

            finalResults.add(subjectResult);
        }

        return finalResults;
    }
}