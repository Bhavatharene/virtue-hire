package com.virtuehire.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaFixer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixSchema() {
        /*
         * try {
         * System.out.println("Checking and fixing database schema index...");
         * 
         * // 1. Drop the incorrect index if it exists
         * // Hibernate's auto-generated index name from the screenshot
         * String wrongIndex = "UKdtpqf2umuoipb83tq9ofl8ahqc";
         * 
         * try {
         * jdbcTemplate.execute("ALTER TABLE assessment_results DROP INDEX " +
         * wrongIndex);
         * System.out.println("Dropped faulty index: " + wrongIndex);
         * } catch (Exception e) {
         * System.out.println("Faulty index not found or already dropped: " +
         * e.getMessage());
         * }
         * 
         * // 2. Add the correct unique index on (candidate_id, subject, level)
         * // Use a stable name so we don't duplicate it
         * try {
         * jdbcTemplate.execute(
         * "ALTER TABLE assessment_results ADD UNIQUE INDEX UK_candidate_subject_level (candidate_id, subject, level)"
         * );
         * System.out.println("Created correct 3-column unique index.");
         * } catch (Exception e) {
         * System.out.println("Correct index already exists or could not be created: " +
         * e.getMessage());
         * }
         * 
         * } catch (Exception e) {
         * System.err.println("Failed to fix database schema: " + e.getMessage());
         * e.printStackTrace();
         * }
         */
    }
}
