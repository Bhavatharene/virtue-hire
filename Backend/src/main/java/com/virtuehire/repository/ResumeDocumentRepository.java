package com.virtuehire.repository;

import com.virtuehire.model.ResumeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeDocumentRepository extends JpaRepository<ResumeDocument, Long> {

    List<ResumeDocument> findByCandidateIdOrderByUpdatedAtDesc(Long candidateId);

    Optional<ResumeDocument> findByIdAndCandidateId(Long id, Long candidateId);

    List<ResumeDocument> findByCandidateId(Long candidateId);
}
