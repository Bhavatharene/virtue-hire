package com.virtuehire.controller;

import com.virtuehire.model.Hr;
import com.virtuehire.model.Payment;
import com.virtuehire.model.PaymentStatus;
import com.virtuehire.service.PaymentService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentRestController {

    private final PaymentService paymentService;

    public PaymentRestController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // ===== GET PAYMENT PLANS =====
    @GetMapping("/plans")
    public ResponseEntity<?> showPlans(HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).body("Not logged in");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("hr", hr);
        response.put("monthlyPrice", paymentService.getPlanPrice("MONTHLY_UNLIMITED"));
        response.put("tenPrice", paymentService.getPlanPrice("TEN_CANDIDATES"));
        response.put("singlePrice", paymentService.getPlanPrice("SINGLE_CANDIDATE"));

        return ResponseEntity.ok(response);
    }

    // ===== PROCESS PAYMENT =====
    @PostMapping("/process-payment")
    public ResponseEntity<?> processPayment(
            @RequestParam String planType,
            HttpSession session
    ) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).body("Not logged in");
        }

        // Initiate payment
        Payment payment = paymentService.initiatePlanPayment(hr, planType);

        // Process payment (mock)
        payment = paymentService.processPlanPayment(payment.getPaymentGatewayId());

        Map<String, Object> response = new HashMap<>();

        if (payment.getStatus() == PaymentStatus.SUCCESS) {

            Long candidateId = (Long) session.getAttribute("pendingCandidateId");
            session.removeAttribute("pendingCandidateId");

            hr = payment.getHr();
            session.setAttribute("hr", hr);

            response.put("success", true);
            response.put("message", "Payment successful!");
            response.put("redirectCandidateId", candidateId);

            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("error", "Payment failed. Try again.");
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ===== PAYMENT HISTORY =====
    @GetMapping("/history")
    public ResponseEntity<?> paymentHistory(HttpSession session) {
        Hr hr = (Hr) session.getAttribute("hr");
        if (hr == null) {
            return ResponseEntity.status(401).body("Not logged in");
        }

        List<Payment> payments = paymentService.getPaymentsByHr(hr.getId());

        return ResponseEntity.ok(payments);
    }

    // ===== CHECK PAYMENT STATUS =====
    @GetMapping("/status/{gatewayId}")
    public ResponseEntity<?> checkPaymentStatus(@PathVariable String gatewayId) {

        Optional<Payment> paymentOpt = paymentService.getPaymentByGatewayId(gatewayId);

        Map<String, Object> response = new HashMap<>();

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            response.put("status", payment.getStatus().toString());
            response.put("amount", payment.getAmount());
            response.put("planType", payment.getPlanType());
        } else {
            response.put("status", "NOT_FOUND");
        }

        return ResponseEntity.ok(response);
    }
}
