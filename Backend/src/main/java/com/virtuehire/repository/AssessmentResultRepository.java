package com.virtuehire.repository;

import com.virtuehire.model.AssessmentResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AssessmentResultRepository extends JpaRepository<AssessmentResult, Long> {

    // Check if a candidate attempted a level in a specific subject
    List<AssessmentResult> findByCandidateIdAndSubjectIgnoreCaseAndLevel(Long candidateId, String subject,
            int level);

    // Fetch all results for a candidate in a specific subject
    List<AssessmentResult> findByCandidateIdAndSubjectIgnoreCase(Long candidateId, String subject);

    // Fetch all results across all subjects
    List<AssessmentResult> findByCandidateId(Long candidateId);

    void deleteByCandidateId(Long candidateId);

    void deleteBySubjectIgnoreCase(String subject);
}
