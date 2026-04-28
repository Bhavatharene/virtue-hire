package com.virtuehire.model;

import jakarta.persistence.*;

/**
 * Stores the problem description for CODING questions.
 * One-to-one with Question.
 *
 * With ddl-auto=update, Hibernate creates this table automatically.
 */
@Entity
@Table(name = "coding_details")
public class CodingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    private Question question;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    public CodingDetail() {}

    // ===== Getters & Setters =====
    public Long getId() { return id; }

    public Question getQuestion() { return question; }
    public void setQuestion(Question question) { this.question = question; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
} 
    

