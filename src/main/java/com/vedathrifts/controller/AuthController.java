package com.vedathrifts.controller;

import com.vedathrifts.dto.request.LoginRequest;
import com.vedathrifts.dto.request.RegisterRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.dto.response.JwtResponse;
import com.vedathrifts.model.User;
import com.vedathrifts.model.Role;
import com.vedathrifts.model.PasswordResetToken;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.repository.PasswordResetTokenRepository;
import com.vedathrifts.security.JwtUtils;
import com.vedathrifts.security.UserDetailsImpl;
import com.vedathrifts.service.EmailService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AuthController {
    
    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private EmailService emailService;
    
    // Handle OPTIONS requests explicitly
    @RequestMapping(method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        return ResponseEntity.ok()
                .header("Access-Control-Allow-Origin", "http://localhost:3000")
                .header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .header("Access-Control-Allow-Credentials", "true")
                .build();
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            log.info("=== LOGIN ATTEMPT ===");
            log.info("Email: {}", loginRequest.getEmail());
            
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(), 
                        loginRequest.getPassword()
                    ));
            
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);
            
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            log.info("Login successful for user: {}", user.getEmail());
            
            return ResponseEntity.ok(new JwtResponse(
                jwt,
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name()
            ));
            
        } catch (Exception e) {
            log.error("Login failed: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid email or password"));
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("========== REGISTRATION ATTEMPT ==========");
        log.info("Email: {}", registerRequest.getEmail());
        
        try {
            // Check if email already exists
            if (userRepository.existsByEmail(registerRequest.getEmail())) {
                log.warn("Email already in use: {}", registerRequest.getEmail());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Email already in use"));
            }
            
            // Create new user
            User user = new User();
            user.setName(registerRequest.getName());
            user.setEmail(registerRequest.getEmail());
            user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
            user.setPhone(registerRequest.getPhone());
            user.setRole(Role.USER);
            
            // Save user
            User savedUser = userRepository.save(user);
            log.info("✅ User saved to database with ID: {}", savedUser.getId());
            log.info("User details - Name: {}, Email: {}", savedUser.getName(), savedUser.getEmail());
            
            // Send welcome email 
            log.info("📧 ATTEMPTING TO SEND WELCOME EMAIL NOW...");
            log.info("📧 Email service instance: {}", emailService != null ? "available" : "NULL");
            
            try {
                // Call email service directly 
                emailService.sendWelcomeEmail(savedUser);
                log.info("✅ Email service method completed");
            } catch (Exception e) {
                log.error("❌ Email service threw exception: {}", e.getMessage(), e);
            }
            
            log.info("✅ Registration successful for: {}", savedUser.getEmail());
            return ResponseEntity.ok(new ApiResponse(true, 
                "User registered successfully. Welcome email sent to " + savedUser.getEmail()));
            
        } catch (Exception e) {
            log.error("❌ Registration failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Registration failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        log.info("========== FORGOT PASSWORD REQUEST ==========");
        log.info("Email: {}", email);
        
        try {
            User user = userRepository.findByEmail(email)
                    .orElse(null);
            
            // Always return success even if email doesn't exist 
            if (user == null) {
                log.info("Email not found, but returning success for security");
                return ResponseEntity.ok(new ApiResponse(true, 
                    "If your email exists in our system, you will receive a password reset link"));
            }
            
            log.info("User found: {} with ID: {}", user.getEmail(), user.getId());
            
            // Delete any existing tokens for this user
            passwordResetTokenRepository.deleteByUser(user);
            log.info("Deleted existing tokens for user: {}", user.getEmail());
            
            // Create new token
            String token = UUID.randomUUID().toString();
            PasswordResetToken resetToken = new PasswordResetToken(token, user);
            passwordResetTokenRepository.save(resetToken);
            log.info("Password reset token created for user: {}", user.getEmail());
            
            // Send email L
            log.info("📧 Attempting to send password reset email to: {}", user.getEmail());
            emailService.sendPasswordResetEmail(user, token);
            
            return ResponseEntity.ok(new ApiResponse(true, 
                "Password reset email sent successfully"));
            
        } catch (Exception e) {
            log.error("Password reset request failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to process request"));
        }
    }
    
    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@RequestParam String token, @RequestParam String newPassword) {
        try {
            log.info("Password reset attempt with token");
            
            PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                    .orElse(null);
            
            if (resetToken == null) {
                log.warn("Invalid password reset token: {}", token);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid or expired token"));
            }
            
            if (resetToken.isExpired()) {
                log.warn("Expired password reset token for user: {}", resetToken.getUser().getEmail());
                passwordResetTokenRepository.delete(resetToken);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Token has expired"));
            }
            
            if (resetToken.isUsed()) {
                log.warn("Used password reset token for user: {}", resetToken.getUser().getEmail());
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Token has already been used"));
            }
            
            // Validate password strength
            if (newPassword.length() < 6) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Password must be at least 6 characters long"));
            }
            
            User user = resetToken.getUser();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            
            // Mark token as used
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
            
            log.info("Password reset successful for user: {}", user.getEmail());
            
            return ResponseEntity.ok(new ApiResponse(true, "Password reset successfully"));
            
        } catch (Exception e) {
            log.error("Password reset failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to reset password"));
        }
    }
    
    @GetMapping("/validate-reset-token")
    public ResponseEntity<?> validateResetToken(@RequestParam String token) {
        try {
            PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                    .orElse(null);
            
            if (resetToken == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid token"));
            }
            
            if (resetToken.isExpired()) {
                passwordResetTokenRepository.delete(resetToken);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Token has expired"));
            }
            
            if (resetToken.isUsed()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Token has already been used"));
            }
            
            return ResponseEntity.ok(new ApiResponse(true, "Token is valid"));
            
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to validate token"));
        }
    }
    
    @PostMapping("/resend-welcome-email")
    public ResponseEntity<?> resendWelcomeEmail(@RequestParam String email) {
        log.info("========== RESEND WELCOME EMAIL ==========");
        log.info("Email: {}", email);
        
        try {
            User user = userRepository.findByEmail(email)
                    .orElse(null);
            
            if (user == null) {
                log.warn("User not found: {}", email);
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "User not found"));
            }
            
            log.info("User found: {} with ID: {}", user.getEmail(), user.getId());
            
            // Send email directly
            emailService.sendWelcomeEmail(user);
            log.info("Welcome email resent to: {}", email);
            
            return ResponseEntity.ok(new ApiResponse(true, "Welcome email resent successfully"));
            
        } catch (Exception e) {
            log.error("Failed to resend welcome email: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to resend email"));
        }
    }
}