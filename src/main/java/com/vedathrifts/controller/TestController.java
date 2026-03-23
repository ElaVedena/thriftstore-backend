package com.vedathrifts.controller;

import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.model.Order;
import com.vedathrifts.model.OrderItem;
import com.vedathrifts.model.User;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.security.UserDetailsImpl;
import com.vedathrifts.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/test")
@CrossOrigin(origins = "*", maxAge = 3600)
public class TestController {

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_EMAIL = "esther.asanda@gmail.com";

    @GetMapping("/email")
    public ResponseEntity<?> testEmail(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            log.info("========== TEST EMAIL ENDPOINT ==========");
            log.info("Current user: {}", currentUser.getUsername());
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            log.info("User found: {} with email: {}", user.getId(), user.getEmail());
            
            // Create a minimal order for testing
            Order testOrder = new Order();
            testOrder.setOrderNumber("TEST-" + UUID.randomUUID().toString().substring(0, 8));
            testOrder.setUser(user);
            testOrder.setTotal(100.0);
            testOrder.setSubtotal(100.0);
            testOrder.setShippingCost(0.0);
            testOrder.setStatus("TEST");
            testOrder.setCreatedAt(LocalDateTime.now());
            
            // Add a test item
            OrderItem item = new OrderItem();
            item.setOrder(testOrder);
            item.setProductName("Test Item");
            item.setPrice(100.0);
            item.setQuantity(1);
            item.setImageUrl("https://example.com/test.jpg");
            testOrder.getItems().add(item);
            
            log.info("Sending test email to: {}", user.getEmail());
            
            if (user.getEmail().equals(TEST_EMAIL)) {
                log.info("Calling email service...");
                emailService.sendOrderConfirmationEmail(testOrder, user);
                log.info("Email service call completed");
                
                return ResponseEntity.ok(new ApiResponse(true, 
                    "Test email sent to " + user.getEmail() + ". Check your inbox and spam folder."));
            } else {
                log.info("📧 Test email NOT sent - only goes to {}", TEST_EMAIL);
                return ResponseEntity.ok(new ApiResponse(true, 
                    "Test email not sent. Only works for " + TEST_EMAIL));
            }
            
        } catch (Exception e) {
            log.error("❌ Test email failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Test email failed: " + e.getMessage()));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        log.info("Test ping endpoint called");
        return ResponseEntity.ok(new ApiResponse(true, "Test controller is working"));
    }
}