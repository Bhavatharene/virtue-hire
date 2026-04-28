package com.virtuehire.repository;

import com.virtuehire.model.CandidateAccessRequest;
import com.virtuehire.model.CandidateAccessRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateAccessRequestRepository extends JpaRepository<CandidateAccessRequest, Long> {

    @EntityGraph(attributePaths = { "hr", "candidate" })
    Optional<CandidateAccessRequest> findByHrIdAndCandidateId(Long hrId, Long candidateId);

    @EntityGraph(attributePaths = { "hr", "candidate" })
    List<CandidateAccessRequest> findByHrId(Long hrId);

    @EntityGraph(attributePaths = { "hr", "candidate" })
    List<CandidateAccessRequest> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "hr", "candidate" })
    List<CandidateAccessRequest> findByStatusOrderByCreatedAtDesc(CandidateAccessRequestStatus status);

    long countByStatus(CandidateAccessRequestStatus status);
}
