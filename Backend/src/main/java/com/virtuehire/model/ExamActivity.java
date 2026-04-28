package com.virtuehire.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_activities")
public class ExamActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long candidateId;
    private String candidateName;
    private String assessmentName;
    private String section;
    private Integer questionNumber;
    private String eventType;
    private String eventDetails;
    private LocalDateTime timestamp;
    private Integer violationCount;
    private String status; // Active, Warning, Suspicious, Submitted
    private Integer timeLeft; // in seconds

    public ExamActivity() {}

    public ExamActivity(Long candidateId, String candidateName, String assessmentName, String section, 
                        Integer questionNumber, String eventType, String eventDetails, 
                        LocalDateTime timestamp, Integer violationCount, String status, Integer timeLeft) {
        this.candidateId = candidateId;
        this.candidateName = candidateName;
        this.assessmentName = assessmentName;
        this.section = section;
        this.questionNumber = questionNumber;
        this.eventType = eventType;
        this.eventDetails = eventDetails;
        this.timestamp = timestamp;
        this.violationCount = violationCount;
        this.status = status;
        this.timeLeft = timeLeft;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }

    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }

    public String getAssessmentName() { return assessmentName; }
    public void setAssessmentName(String assessmentName) { this.assessmentName = assessmentName; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public Integer getQuestionNumber() { return questionNumber; }
    public void setQuestionNumber(Integer questionNumber) { this.questionNumber = questionNumber; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getEventDetails() { return eventDetails; }
    public void setEventDetails(String eventDetails) { this.eventDetails = eventDetails; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public Integer getViolationCount() { return violationCount; }
    public void setViolationCount(Integer violationCount) { this.violationCount = violationCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTimeLeft() { return timeLeft; }
    public void setTimeLeft(Integer timeLeft) { this.timeLeft = timeLeft; }
}
