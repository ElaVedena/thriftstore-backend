package com.vedathrifts.controller;

import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.model.User;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugEmailController {

    private final EmailService emailService;
    private final UserRepository userRepository;

    @PostMapping("/test-email")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> testEmail(@AuthenticationPrincipal UserDetails currentUser) {
        log.info("========== DEBUG EMAIL TEST ==========");
        log.info("Testing email for authenticated user: {}", currentUser.getUsername());
        
        try {
            User user = userRepository.findByEmail(currentUser.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            log.info("Found user: {} with ID: {}", user.getEmail(), user.getId());
            
            // Test welcome email
            emailService.sendWelcomeEmail(user);
            
            return ResponseEntity.ok(new ApiResponse(true, 
                "Test email sent! Check logs and your inbox at " + user.getEmail()));
            
        } catch (Exception e) {
            log.error("Debug email test failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Email test failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/check-config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> checkConfig() {
        log.info("========== CHECK EMAIL CONFIG ==========");
        
        try {
            // Check if EmailService bean exists
            log.info("EmailService bean exists: {}", emailService != null);
            
            return ResponseEntity.ok(new ApiResponse(true, 
                "Check logs for email configuration details"));
            
        } catch (Exception e) {
            log.error("Config check failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Config check failed"));
        }
    }
}