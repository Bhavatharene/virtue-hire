package com.virtuehire.model;

import jakarta.persistence.*;

@Entity
@Table(name = "assessment_configs")
public class AssessmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String subject; // Test Name

    @Column(nullable = false)
    private int sectionNumber;

    @Column(nullable = false)
    private String sectionName;

    @Column(nullable = false)
    private int timeLimit; // In minutes

    @Column(nullable = false)
    private int questionCount = 10;

    public AssessmentConfig() {
    }

    public AssessmentConfig(String subject, int sectionNumber, String sectionName, int timeLimit, int questionCount) {
        this.subject = subject;
        this.sectionNumber = sectionNumber;
        this.sectionName = sectionName;
        this.timeLimit = timeLimit;
        this.questionCount = questionCount;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getSectionNumber() {
        return sectionNumber;
    }

    public void setSectionNumber(int sectionNumber) {
        this.sectionNumber = sectionNumber;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }
}
