package com.virtuehire.controller;

import com.virtuehire.model.Candidate;
import com.virtuehire.model.Hr;
import com.virtuehire.service.CandidateService;
import com.virtuehire.service.HrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import com.virtuehire.util.JwtUtil;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "https://admin.virtuehire.in", allowCredentials = "true")
public class AuthRestController {

    private final CandidateService candidateService;
    private final HrService hrService;
    private final JwtUtil jwtUtil;

    public AuthRestController(CandidateService candidateService,
            HrService hrService,
            JwtUtil jwtUtil) {
        this.candidateService = candidateService;
        this.hrService = hrService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request,
            HttpSession session) {

        String email = request.get("email");
        String password = request.get("password");
        String role = request.get("role");

        if (email == null || password == null || role == null) {
            return ResponseEntity.status(400).body(Map.of("error", "Email, password, and role are required"));
        }

        switch (role.toUpperCase()) {

            case "CANDIDATE":
                Candidate candidate = candidateService.login(email, password);
                if (candidate != null) {
                    if (!candidateService.isEmailVerified(candidate)) {
                        return ResponseEntity.status(403).body(Map.of(
                                "error", "Please verify your email using OTP",
                                "emailVerified", false));
                    }

                    session.setAttribute("user", candidate);
                    session.setAttribute("candidate", candidate);
                    session.setAttribute("role", "CANDIDATE");
                    String token = jwtUtil.generateToken(email, "CANDIDATE");
                    return ResponseEntity.ok(Map.of(
                            "message", "Candidate login successful",
                            "role", "CANDIDATE",
                            "token", token,
                            "user", candidate));
                }
                return ResponseEntity.status(401).body(Map.of("error", "Invalid Candidate credentials"));

            case "HR":
                // MASTER HR CREDENTIALS FOR TESTING
                if ("hr@login.com".equalsIgnoreCase(email.trim()) && "123".equals(password.trim())) {
                    Hr masterHr = new Hr();
                    masterHr.setId(0L); // special ID for master
                    masterHr.setFullName("Master HR");
                    masterHr.setEmail("hr@login.com");
                    masterHr.setVerified(true);
                    session.setAttribute("hr", masterHr);
                    session.setAttribute("user", masterHr);
                    session.setAttribute("role", "HR");
                    String token = jwtUtil.generateToken(email, "HR");
                    return ResponseEntity.ok(Map.of(
                            "message", "Master HR login successful",
                            "role", "HR",
                            "token", token,
                            "user", masterHr));
                }

                Hr hr = hrService.login(email, password);
                if (hr != null) {
                    if (!Boolean.TRUE.equals(hr.getEmailVerified())) {
                        return ResponseEntity.status(403).body(Map.of(
                                "error", "Please verify your email first. Check your inbox for the HR verification code.",
                                "emailVerified", false,
                                "adminApproved", Boolean.TRUE.equals(hr.getVerified())));
                    }

                    if (!Boolean.TRUE.equals(hr.getVerified())) {
                        return ResponseEntity.status(403).body(Map.of(
                                "error", "Your email is verified. Your HR account is waiting for admin approval.",
                                "emailVerified", true,
                                "adminApproved", false));
                    }

                    session.setAttribute("hr", hr);
                    session.setAttribute("user", hr);
                    session.setAttribute("role", "HR");
                    String token = jwtUtil.generateToken(email, "HR");
                    return ResponseEntity.ok(Map.of(
                            "message", "HR login successful",
                            "role", "HR",
                            "token", token,
                            "user", hr));
                }
                return ResponseEntity.status(401).body(Map.of("error", "Invalid HR credentials"));

            case "ADMIN":
                if ("admin@login.com".equalsIgnoreCase(email.trim())
                        && "123".equals(password.trim())) {
                    session.setAttribute("role", "ADMIN");
                    String token = jwtUtil.generateToken(email.trim(), "ADMIN");
                    return ResponseEntity.ok(Map.of(
                            "message", "Admin login successful",
                            "role", "ADMIN",
                            "token", token,
                            "user", Map.of("email", email.trim())));
                }
                return ResponseEntity.status(401).body(Map.of("error", "Invalid Admin credentials"));

            default:
                return ResponseEntity.status(400).body(Map.of("error", "Unknown role"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
