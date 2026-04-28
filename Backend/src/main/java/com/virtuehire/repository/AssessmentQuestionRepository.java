package com.virtuehire.repository;

import com.virtuehire.model.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.virtuehire.model.Question;
import java.util.List;

@Repository
public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Long> {
    List<AssessmentQuestion> findByAssessmentId(Long assessmentId);
    List<AssessmentQuestion> findBySectionId(Long sectionId);

    @Query("SELECT q FROM AssessmentQuestion aq JOIN aq.question q LEFT JOIN FETCH q.options WHERE aq.section.id = :sectionId")
    List<Question> findQuestionsBySectionId(@Param("sectionId") Long sectionId);
}
