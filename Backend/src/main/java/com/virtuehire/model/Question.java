package com.virtuehire.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Difficulty level: 1 = Easy, 2 = Medium, 3 = Hard
    @Column(nullable = false)
    private int level;

    @Column(nullable = false, length = 1000)
    private String text;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text", nullable = false)
    private List<String> options;

    @Column(nullable = false)
    private String correctAnswer;

    // NEW FIELD — SUBJECT (Java, C, C++, SQL, Python, etc.) - Acts as Test Name
    @Column(nullable = false)
    private String subject;

    // NEW FIELD — SECTION NAME (Aptitude, Technical, etc.)
    @Column(nullable = true)
    private String sectionName;

    // ─── CODING EXTENSION — NEW FIELDS (safe defaults, won't break existing rows) ───

    /**
     * false for all existing MCQ questions (DB default).
     * true only for CODING type questions uploaded via CSV.
     */
    @Column(name = "has_compiler", nullable = false)
    private boolean hasCompiler = false;

    /**
     * "MCQ" for all existing questions (DB default).
     * "CODING" for coding questions.
     */
    @Column(name = "question_type", nullable = false, length = 10)
    private String questionType = "MCQ";

    // ===== Constructors =====
    public Question() {
    }

    // Original constructor — UNCHANGED, still works for MCQ/MSQ
    public Question(int level, String text, List<String> options, String correctAnswer, String subject,
            String sectionName) {
        this.level = level;
        this.text = text;
        this.options = options;
        this.correctAnswer = correctAnswer;
        this.subject = subject;
        this.sectionName = sectionName;
        this.hasCompiler = false;
        this.questionType = "MCQ";
    }

    // New constructor for CODING questions
    public Question(String subject, String sectionName, boolean hasCompiler, String questionType) {
        this.level = 1;
        this.subject = subject;
        this.sectionName = sectionName;
        this.hasCompiler = hasCompiler;
        this.questionType = questionType;
        this.text = "";        // not used for CODING
        this.correctAnswer = ""; // not used for CODING
    }

    // ===== Getters & Setters — ALL ORIGINAL ONES UNCHANGED =====
    public Long getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public void setCorrectAnswer(String correctAnswer) {
        this.correctAnswer = correctAnswer;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getSectionName() {
        return sectionName;
    }

    public void setSectionName(String sectionName) {
        this.sectionName = sectionName;
    }

    // ─── NEW getters/setters for coding fields ───

    public boolean isHasCompiler() {
        return hasCompiler;
    }

    public void setHasCompiler(boolean hasCompiler) {
        this.hasCompiler = hasCompiler;
    }

    public String getQuestionType() {
        return questionType;
    }

    public void setQuestionType(String questionType) {
        this.questionType = questionType;
    }
}