package com.virtuehire.repository;

import com.virtuehire.model.CodingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodingDetailRepository extends JpaRepository<CodingDetail, Long> {
    Optional<CodingDetail> findByQuestionId(Long questionId);
}