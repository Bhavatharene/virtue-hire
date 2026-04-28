package com.virtuehire.repository;

import com.virtuehire.model.AssessmentSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssessmentSectionRepository extends JpaRepository<AssessmentSection, Long> {
    List<AssessmentSection> findByAssessmentIdOrderBySectionNumberAsc(Long assessmentId);

    @Query("SELECT s FROM AssessmentSection s WHERE s.assessment.assessmentName = :assessmentName AND s.sectionNumber = :sectionNumber")
    Optional<AssessmentSection> findByAssessmentNameAndSectionNumber(String assessmentName, int sectionNumber);
}

