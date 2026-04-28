package com.virtuehire.repository;

import com.virtuehire.model.AssessmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AssessmentConfigRepository extends JpaRepository<AssessmentConfig, Long> {
    List<AssessmentConfig> findBySubjectOrderBySectionNumberAsc(String subject);

    Optional<AssessmentConfig> findBySubjectAndSectionNumber(String subject, int sectionNumber);

    @Query("SELECT DISTINCT a.subject FROM AssessmentConfig a")
    List<String> findDistinctSubject();
}
