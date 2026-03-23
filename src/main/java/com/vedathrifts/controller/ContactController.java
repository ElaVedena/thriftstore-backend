package com.vedathrifts.controller;

import com.vedathrifts.dto.request.ContactRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/contact")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ContactController {

    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<?> sendContactMessage(@Valid @RequestBody ContactRequest request) {
        try {
            log.info("Received contact message from: {} ({})", request.getName(), request.getEmail());
            
            // Send email using Resend
            emailService.sendContactEmail(request);
            
            return ResponseEntity.ok(new ApiResponse(
                true, 
                "Thank you for reaching out! We'll get back to you within 24 hours."
            ));
            
        } catch (Exception e) {
            log.error("Error sending contact email: {}", e.getMessage());
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to send message. Please try again later."));
        }
    }
}