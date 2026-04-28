package com.virtuehire.repository;

import com.virtuehire.model.ExamActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExamActivityRepository extends JpaRepository<ExamActivity, Long> {
    List<ExamActivity> findByCandidateId(Long candidateId);
    void deleteByCandidateId(Long candidateId);
    List<ExamActivity> findByStatus(String status);
}
