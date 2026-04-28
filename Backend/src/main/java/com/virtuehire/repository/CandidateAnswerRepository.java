package com.virtuehire.repository;

import com.virtuehire.model.CandidateAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CandidateAnswerRepository extends JpaRepository<CandidateAnswer, Long> {

    List<CandidateAnswer> findByResultId(Long resultId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CandidateAnswer ca WHERE ca.resultId = :resultId")
    void deleteByResultId(@Param("resultId") Long resultId);
}