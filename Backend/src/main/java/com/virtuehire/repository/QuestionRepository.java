package com.virtuehire.repository;

import com.virtuehire.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    // Existing method
    List<Question> findByLevel(int level);

    // NEW: Get all questions for a specific subject (Java, C, Python, etc.)
    List<Question> findBySubject(String subject);

    // NEW: Get questions for a subject + level (ex: Java, Level 1)
    List<Question> findBySubjectAndLevel(String subject, int level);

    List<Question> findBySubjectAndHasCompiler(String subject, boolean hasCompiler);
    
 // Check if a question already exists by text and subject
    boolean existsByTextAndSubject(String text, String subject);

}
