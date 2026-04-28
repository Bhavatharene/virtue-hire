package com.virtuehire.repository;

import com.virtuehire.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    // Case-insensitive login check
    @Query("SELECT c FROM Candidate c WHERE lower(c.email) = lower(:email) AND c.password = :password")
    Optional<Candidate> login(@Param("email") String email, @Param("password") String password);

    Optional<Candidate> findByEmail(String email);
}
