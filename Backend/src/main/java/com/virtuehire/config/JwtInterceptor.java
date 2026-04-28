package com.virtuehire.config;

import com.virtuehire.model.Candidate;
import com.virtuehire.model.Hr;
import com.virtuehire.service.CandidateService;
import com.virtuehire.service.HrService;
import com.virtuehire.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private HrService hrService;

    @Autowired
    private CandidateService candidateService;

    private boolean hasAuthenticatedSession(HttpSession session) {
        if (session == null) {
            return false;
        }

        Object role = session.getAttribute("role");
        if ("ADMIN".equals(role)) {
            return true;
        }

        return session.getAttribute("hr") != null || session.getAttribute("candidate") != null
                || session.getAttribute("user") != null;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Allow CORS pre-flight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Public endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/") || path.startsWith("/api/public/") || 
            path.startsWith("/api/hrs/login") || path.startsWith("/api/hrs/register") || 
            path.startsWith("/api/hrs/verify-email")) {
            return true;
        }

        HttpSession existingSession = request.getSession(false);
        if (hasAuthenticatedSession(existingSession)) {
            return true;
        }

        final String authorizationHeader = request.getHeader("Authorization");

        String email = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                email = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"Invalid or expired token\"}");
                return false;
            }
        }

        if (email != null && jwtUtil.validateToken(jwt, email)) {
            String role = jwtUtil.extractClaim(jwt, claims -> claims.get("role", String.class));
            HttpSession session = request.getSession();

            // Populate session for compatibility with existing controllers
            if ("HR".equalsIgnoreCase(role)) {
                Optional<Hr> hr = hrService.findByEmail(email);
                if (hr.isPresent()) {
                    session.setAttribute("hr", hr.get());
                    session.setAttribute("user", hr.get());
                    session.setAttribute("role", "HR");
                } else if ("hr@login.com".equals(email)) {
                    // Handle Master HR
                    Hr masterHr = new Hr();
                    masterHr.setId(0L);
                    masterHr.setEmail("hr@login.com");
                    masterHr.setFullName("Master HR");
                    masterHr.setVerified(true);
                    session.setAttribute("hr", masterHr);
                    session.setAttribute("user", masterHr);
                    session.setAttribute("role", "HR");
                }
            } else if ("CANDIDATE".equalsIgnoreCase(role)) {
                Candidate candidate = candidateService.findByEmail(email);
                if (candidate != null) {
                    session.setAttribute("candidate", candidate);
                    session.setAttribute("user", candidate);
                    session.setAttribute("role", "CANDIDATE");
                }
            } else if ("ADMIN".equalsIgnoreCase(role)) {
                session.setAttribute("role", "ADMIN");
            }

            request.setAttribute("userEmail", email);
            request.setAttribute("userRole", role);
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\": \"Unauthorized - Bearer token missing or invalid\"}");
        return false;
    }
}
