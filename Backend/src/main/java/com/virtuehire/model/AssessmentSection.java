package com.virtuehire.model;

import jakarta.persistence.*;

@Entity
@Table(name = "assessment_sections")
public class AssessmentSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Column(nullable = false)
    private int sectionNumber;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private int sectionTime; // in minutes

    @Column(nullable = false)
    private int passPercentage;

    @Column(nullable = false, length = 20)
    private String sectionMode = "NO_COMPILER";

    @Column(length = 255)
    private String supportedLanguages;

    public AssessmentSection() {
    }

    public AssessmentSection(Assessment assessment, int sectionNumber, String subject, int questionCount, int sectionTime, int passPercentage) {
        this.assessment = assessment;
        this.sectionNumber = sectionNumber;
        this.subject = subject;
        this.questionCount = questionCount;
        this.sectionTime = sectionTime;
        this.passPercentage = passPercentage;
    }

    public AssessmentSection(Assessment assessment, int sectionNumber, String subject, int questionCount, int sectionTime,
            int passPercentage, String sectionMode, String supportedLanguages) {
        this.assessment = assessment;
        this.sectionNumber = sectionNumber;
        this.subject = subject;
        this.questionCount = questionCount;
        this.sectionTime = sectionTime;
        this.passPercentage = passPercentage;
        this.sectionMode = sectionMode;
        this.supportedLanguages = supportedLanguages;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public Assessment getAssessment() {
        return assessment;
    }

    public void setAssessment(Assessment assessment) {
        this.assessment = assessment;
    }

    public int getSectionNumber() {
        return sectionNumber;
    }

    public void setSectionNumber(int sectionNumber) {
        this.sectionNumber = sectionNumber;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public int getSectionTime() {
        return sectionTime;
    }

    public void setSectionTime(int sectionTime) {
        this.sectionTime = sectionTime;
    }

    public int getPassPercentage() {
        return passPercentage;
    }

    public void setPassPercentage(int passPercentage) {
        this.passPercentage = passPercentage;
    }

    public String getSectionMode() {
        return sectionMode;
    }

    public void setSectionMode(String sectionMode) {
        this.sectionMode = sectionMode;
    }

    public String getSupportedLanguages() {
        return supportedLanguages;
    }

    public void setSupportedLanguages(String supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }
}
