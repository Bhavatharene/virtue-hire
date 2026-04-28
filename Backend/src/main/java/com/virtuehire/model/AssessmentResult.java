package com.virtuehire.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.io.Serializable;

@Entity
@Table(name = "assessment_results", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "candidate_id", "level", "subject" })
})
public class AssessmentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private int score;

    @Lob
    @Column(name = "answers_json", columnDefinition = "LONGTEXT")
    private String answersJson;

    private LocalDateTime attemptedAt = LocalDateTime.now();

    // ✅ New field to track failed lock time
    private LocalDateTime lockedAt;

    public AssessmentResult() {
    }

    public AssessmentResult(Candidate candidate, String subject, int level, int score) {
        this.candidate = candidate;
        this.subject = subject;
        this.level = level;
        this.score = score;
        this.attemptedAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate candidate) {
        this.candidate = candidate;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getAnswersJson() {
        return answersJson;
    }

    public void setAnswersJson(String answersJson) {
        this.answersJson = answersJson;
    }

    public LocalDateTime getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(LocalDateTime attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }
}
