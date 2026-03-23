package com.vedathrifts.controller;

import com.vedathrifts.dto.request.OrderRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.dto.response.OrderResponse;
import com.vedathrifts.dto.response.OrderItemResponse;
import com.vedathrifts.model.Order;
import com.vedathrifts.model.OrderItem;
import com.vedathrifts.model.User;
import com.vedathrifts.repository.OrderRepository;
import com.vedathrifts.repository.ProductRepository;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.security.UserDetailsImpl;
import com.vedathrifts.service.EmailService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private EmailService emailService;
    
    // Your Resend account email 
    private static final String TEST_EMAIL = "esther.asanda@gmail.com";

    @PostMapping
    public ResponseEntity<?> createOrder(@Valid @RequestBody OrderRequest orderRequest,
                                         @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            log.info("========== ORDER CREATION ==========");
            log.info("Creating order for user: {}", currentUser.getId());
            
            User user = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Order order = new Order();
            String orderNumber = "VTH" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            order.setOrderNumber(orderNumber);
            order.setUser(user);
            order.setSubtotal(orderRequest.getSubtotal());
            order.setShippingCost(orderRequest.getShippingCost());
            order.setTotal(orderRequest.getTotal());
            order.setStatus("PENDING"); // Order starts as PENDING
            order.setPaymentMethod(orderRequest.getPaymentMethod() != null ? 
                orderRequest.getPaymentMethod() : "M-PESA");
            order.setPaymentCode(orderRequest.getPaymentCode());
            order.setShippingAddress(orderRequest.getShippingAddress());
            order.setCity(orderRequest.getCity());
            order.setCounty(orderRequest.getCounty());
            order.setPhone(orderRequest.getPhone());
            order.setCreatedAt(LocalDateTime.now());

            // Create order items
            if (orderRequest.getItems() != null) {
                log.info("Order items: {}", orderRequest.getItems().size());
                for (OrderRequest.OrderItemRequest itemRequest : orderRequest.getItems()) {
                    OrderItem item = new OrderItem();
                    item.setOrder(order);
                    item.setProductName(itemRequest.getProductName());
                    item.setPrice(itemRequest.getPrice());
                    item.setQuantity(itemRequest.getQuantity());
                    item.setSize(itemRequest.getSize());
                    item.setImageUrl(itemRequest.getImageUrl());
                    
                    //Set the product if productId is provided
                    if (itemRequest.getProductId() != null) {
                        productRepository.findById(itemRequest.getProductId())
                            .ifPresentOrElse(
                                product -> {
                                    item.setProduct(product);
                                    log.debug("  - Found product with ID: {}", product.getId());
                                },
                                () -> log.warn("  - Product with ID {} not found in database", itemRequest.getProductId())
                            );
                    } else {
                        log.debug("  - No productId provided for item: {}", itemRequest.getProductName());
                    }
                    
                    order.getItems().add(item);
                    
                    log.info("  - Item: {} x{} @ KSh {}", 
                        itemRequest.getProductName(), 
                        itemRequest.getQuantity(), 
                        itemRequest.getPrice());
                }
            }

            Order savedOrder = orderRepository.save(order);
            log.info("✅ Order saved with ID: {} and number: {}", 
                savedOrder.getId(), savedOrder.getOrderNumber());
            
            
            OrderResponse response = OrderResponse.fromOrder(savedOrder);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("order", response);
            responseData.put("emailStatus", "Email will be sent after payment confirmation");

            return ResponseEntity.ok(new ApiResponse(true, 
                "Order created successfully. Please complete payment to receive confirmation email.", 
                responseData));
            
        } catch (Exception e) {
            log.error("❌ Failed to create order: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to create order: " + e.getMessage()));
        }
    }

    // PAYMENT CALLBACK METHODS 
    /**
     * This method should be called from your M-Pesa callback when payment is successful
     */
    public void handleSuccessfulPayment(String orderNumber, String mpesaReceiptNumber, String checkoutRequestId) {
        try {
            log.info("========== HANDLING SUCCESSFUL PAYMENT ==========");
            log.info("Order number: {}, Receipt: {}", orderNumber, mpesaReceiptNumber);
            
            Order order = orderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("Order not found with number: " + orderNumber));
            
            // Update order status to PAID
            String oldStatus = order.getStatus();
            order.setStatus("PAID");
            order.setMpesaReceiptNumber(mpesaReceiptNumber);
            order.setCheckoutRequestId(checkoutRequestId);
            order.setUpdatedAt(LocalDateTime.now());
            
            Order updatedOrder = orderRepository.save(order);
            log.info("✅ Order {} status updated from {} to PAID", orderNumber, oldStatus);
            
            User user = order.getUser();
            log.info("📧 Sending order confirmation email to {} after successful payment", user.getEmail());
            
            // Send email synchronously for test email
            if (user.getEmail().equals(TEST_EMAIL)) {
                try {
                    emailService.sendOrderConfirmationEmail(updatedOrder, user);
                    log.info("✅ Order confirmation email sent to test email after payment");
                } catch (Exception e) {
                    log.error("❌ Failed to send confirmation email after payment: {}", e.getMessage(), e);
                }
            } else {
                CompletableFuture.runAsync(() -> {
                    try {
                        emailService.sendOrderConfirmationEmail(updatedOrder, user);
                        log.info("✅ Order confirmation email sent to {} after payment", user.getEmail());
                    } catch (Exception e) {
                        log.error("❌ Failed to send confirmation email after payment: {}", e.getMessage(), e);
                    }
                });
            }
            
        } catch (Exception e) {
            log.error("❌ Error handling successful payment: {}", e.getMessage(), e);
        }
    }
    
    /**
     * This method should be called from your M-Pesa callback when payment fails
     */
    public void handleFailedPayment(String orderNumber, String failureReason) {
        try {
            log.info("========== HANDLING FAILED PAYMENT ==========");
            log.info("Order number: {}, Reason: {}", orderNumber, failureReason);
            
            Order order = orderRepository.findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new RuntimeException("Order not found with number: " + orderNumber));
            
            // Update order status to FAILED
            order.setStatus("PAYMENT_FAILED");
            order.setPaymentFailureReason(failureReason);
            order.setUpdatedAt(LocalDateTime.now());
            
            orderRepository.save(order);
            log.info("✅ Order {} status updated to PAYMENT_FAILED", orderNumber);
            
            
        } catch (Exception e) {
            log.error("❌ Error handling failed payment: {}", e.getMessage(), e);
        }
    }

    // TEST ENDPOINTS FOR EMAIL
    
    @GetMapping("/test-email")
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
                emailService.sendOrderConfirmationEmail(testOrder, user);
                log.info("✅ Test order confirmation email sent to: {}", user.getEmail());
                
                return ResponseEntity.ok(new ApiResponse(true, 
                    "Test email sent to " + user.getEmail() + ". Check your inbox."));
            } else {
                log.info("📧 Test email NOT sent - in testing mode, emails only go to {}", TEST_EMAIL);
                
                return ResponseEntity.ok(new ApiResponse(true, 
                    "Test email not sent. In testing mode, emails only go to " + TEST_EMAIL + 
                    ". Your email is: " + user.getEmail()));
            }
            
        } catch (Exception e) {
            log.error("❌ Test email failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Test email failed: " + e.getMessage()));
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        log.info("Ping endpoint called");
        return ResponseEntity.ok(new ApiResponse(true, "Order controller is working"));
    }

    // REGULAR ENDPOINTS
    @GetMapping("/my-orders")
    public ResponseEntity<?> getMyOrders(@AuthenticationPrincipal UserDetailsImpl currentUser,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        try {
            log.info("Fetching orders for user: {}", currentUser.getId());
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Order> ordersPage = orderRepository.findByUserId(currentUser.getId(), pageable);
            
            // Convert to DTOs
            Page<OrderResponse> orderResponses = ordersPage.map(OrderResponse::fromOrder);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", orderResponses.getContent());
            response.put("totalPages", orderResponses.getTotalPages());
            response.put("totalElements", orderResponses.getTotalElements());
            response.put("currentPage", orderResponses.getNumber());
            response.put("pageSize", orderResponses.getSize());

            return ResponseEntity.ok(new ApiResponse(true, "Orders retrieved successfully", response));
            
        } catch (Exception e) {
            log.error("Failed to fetch orders: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to fetch orders: " + e.getMessage()));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrderById(@PathVariable Long orderId,
                                          @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            log.info("Fetching order with ID: {} for user: {}", orderId, currentUser.getId());
            
            Order order = orderRepository.findById(orderId)
                    .orElse(null);

            if (order == null) {
                log.warn("Order not found with ID: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            // Check if order belongs to user or user is admin
            boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
            if (!order.getUser().getId().equals(currentUser.getId()) && !isAdmin) {
                log.warn("Access denied for user {} to order {}", currentUser.getId(), orderId);
                return ResponseEntity.status(403).body(new ApiResponse(false, "Access denied"));
            }

            // Return DTO instead of entity
            OrderResponse response = OrderResponse.fromOrder(order);

            return ResponseEntity.ok(new ApiResponse(true, "Order retrieved successfully", response));
            
        } catch (Exception e) {
            log.error("Failed to fetch order: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to fetch order: " + e.getMessage()));
        }
    }

    @GetMapping("/track/{orderNumber}")
    public ResponseEntity<?> trackOrder(@PathVariable String orderNumber) {
        try {
            log.info("Tracking order with number: {}", orderNumber);
            
            Order order = orderRepository.findByOrderNumber(orderNumber)
                    .orElse(null);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> trackingInfo = new HashMap<>();
            trackingInfo.put("orderNumber", order.getOrderNumber());
            trackingInfo.put("status", order.getStatus());
            trackingInfo.put("estimatedDelivery", calculateEstimatedDelivery(order));
            trackingInfo.put("currentLocation", getCurrentLocation(order));
            trackingInfo.put("timeline", generateOrderTimeline(order));

            return ResponseEntity.ok(new ApiResponse(true, "Order tracked successfully", trackingInfo));
            
        } catch (Exception e) {
            log.error("Failed to track order: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to track order: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllOrders(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String status) {
        try {
            log.info("Admin fetching all orders, page: {}, size: {}, status: {}", page, size, status);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Order> ordersPage;

            if (status != null && !status.isEmpty()) {
                ordersPage = orderRepository.findByStatus(status, pageable);
            } else {
                ordersPage = orderRepository.findAll(pageable);
            }

            // Convert to DTOs
            Page<OrderResponse> orderResponses = ordersPage.map(OrderResponse::fromOrder);
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", orderResponses.getContent());
            response.put("totalPages", orderResponses.getTotalPages());
            response.put("totalElements", orderResponses.getTotalElements());
            response.put("currentPage", orderResponses.getNumber());
            response.put("pageSize", orderResponses.getSize());

            return ResponseEntity.ok(new ApiResponse(true, "Orders retrieved successfully", response));
            
        } catch (Exception e) {
            log.error("Failed to fetch orders: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to fetch orders: " + e.getMessage()));
        }
    }

    @PutMapping("/admin/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long orderId,
                                               @RequestParam String status) {
        try {
            log.info("Admin updating order {} status to: {}", orderId, status);
            
            Order order = orderRepository.findById(orderId)
                    .orElse(null);

            if (order == null) {
                log.warn("Order not found with ID: {}", orderId);
                return ResponseEntity.notFound().build();
            }

            String oldStatus = order.getStatus();
            order.setStatus(status.toUpperCase());
            order.setUpdatedAt(LocalDateTime.now());
            Order updatedOrder = orderRepository.save(order);
            
            log.info("Order {} status updated from {} to {}", orderId, oldStatus, status);
            
            User user = order.getUser();
            
            // Handle email based on Resend testing restrictions
            if (user.getEmail().equals(TEST_EMAIL)) {
                try {
                    emailService.sendOrderStatusUpdateEmail(updatedOrder, user, oldStatus, status);
                    log.info("✅ Status update email sent to test email: {}", user.getEmail());
                } catch (Exception e) {
                    log.error("❌ Failed to send status update email: {}", e.getMessage());
                }
            } else {
               
                CompletableFuture.runAsync(() -> {
                    try {
                        emailService.sendOrderStatusUpdateEmail(updatedOrder, user, oldStatus, status);
                        log.info("✅ Status update email sent to: {}", user.getEmail());
                    } catch (Exception e) {
                        log.error("❌ Failed to send status update email: {}", e.getMessage());
                    }
                });
            }

            // Return DTO instead of entity
            OrderResponse response = OrderResponse.fromOrder(updatedOrder);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("order", response);
            
            String emailMessage = user.getEmail().equals(TEST_EMAIL) ? 
                "Status update email sent to customer" :
                "Status updated. Status update email will be sent to customer.";
            
            responseData.put("emailStatus", emailMessage);

            return ResponseEntity.ok(new ApiResponse(true, 
                "Order status updated successfully. " + emailMessage, 
                responseData));
            
        } catch (Exception e) {
            log.error("Failed to update order status: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to update order status: " + e.getMessage()));
        }
    }
    
    @PostMapping("/admin/{orderId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        try {
            log.info("Admin cancelling order: {}", orderId);
            
            Order order = orderRepository.findById(orderId)
                    .orElse(null);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            // Check if order can be cancelled 
            if (!order.getStatus().equals("PENDING") && !order.getStatus().equals("PROCESSING")) {
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Order cannot be cancelled at this stage"));
            }

            String oldStatus = order.getStatus();
            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            Order updatedOrder = orderRepository.save(order);
            
            log.info("Order {} cancelled successfully", orderId);
            
            User user = order.getUser();
            
           
            if (user.getEmail().equals(TEST_EMAIL)) {
             
                try {
                    emailService.sendOrderStatusUpdateEmail(updatedOrder, user, oldStatus, "CANCELLED");
                    log.info("✅ Cancellation email sent to test email: {}", user.getEmail());
                } catch (Exception e) {
                    log.error("❌ Failed to send cancellation email: {}", e.getMessage());
                }
            } else {
              
                CompletableFuture.runAsync(() -> {
                    try {
                        emailService.sendOrderStatusUpdateEmail(updatedOrder, user, oldStatus, "CANCELLED");
                        log.info("✅ Cancellation email sent to: {}", user.getEmail());
                    } catch (Exception e) {
                        log.error("❌ Failed to send cancellation email: {}", e.getMessage());
                    }
                });
            }

            OrderResponse response = OrderResponse.fromOrder(updatedOrder);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("order", response);
            
            String emailMessage = user.getEmail().equals(TEST_EMAIL) ? 
                "Cancellation email sent to customer" :
                "Order cancelled. Cancellation email will be sent to customer.";
            
            responseData.put("emailStatus", emailMessage);
            
            return ResponseEntity.ok(new ApiResponse(true, 
                "Order cancelled successfully. " + emailMessage, 
                responseData));
            
        } catch (Exception e) {
            log.error("Failed to cancel order: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to cancel order: " + e.getMessage()));
        }
    }
    
    @PostMapping("/{orderId}/resend-confirmation")
    public ResponseEntity<?> resendConfirmationEmail(@PathVariable Long orderId,
                                                     @AuthenticationPrincipal UserDetailsImpl currentUser) {
        try {
            log.info("========== RESEND ORDER CONFIRMATION ==========");
            log.info("Resending confirmation email for order: {}", orderId);
            
            Order order = orderRepository.findById(orderId)
                    .orElse(null);

            if (order == null) {
                return ResponseEntity.notFound().build();
            }

            // Check if order belongs to user
            if (!order.getUser().getId().equals(currentUser.getId())) {
                return ResponseEntity.status(403).body(new ApiResponse(false, "Access denied"));
            }

            User user = order.getUser();
            
            if (user.getEmail().equals(TEST_EMAIL)) {
              
                try {
                    emailService.sendOrderConfirmationEmail(order, user);
                    log.info("✅ Order confirmation email resent to test email: {}", user.getEmail());
                    return ResponseEntity.ok(new ApiResponse(true, 
                        "Confirmation email resent to " + user.getEmail()));
                } catch (Exception e) {
                    log.error("❌ Failed to resend confirmation email: {}", e.getMessage());
                    return ResponseEntity.status(500)
                        .body(new ApiResponse(false, "Failed to resend confirmation email: " + e.getMessage()));
                }
            } else {
               
                CompletableFuture.runAsync(() -> {
                    try {
                        emailService.sendOrderConfirmationEmail(order, user);
                        log.info("✅ Order confirmation email resent to: {}", user.getEmail());
                    } catch (Exception e) {
                        log.error("❌ Failed to resend confirmation email: {}", e.getMessage());
                    }
                });
                
                return ResponseEntity.ok(new ApiResponse(true, 
                    "Confirmation email will be resent to " + user.getEmail()));
            }
            
        } catch (Exception e) {
            log.error("Failed to resend confirmation email: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to resend confirmation email"));
        }
    }
    
    /**
     * Helper method to calculate estimated delivery date
     */
    private String calculateEstimatedDelivery(Order order) {
        if (order.getStatus().equals("DELIVERED")) {
            return "Delivered on " + order.getUpdatedAt().toLocalDate().toString();
        }
        
        if (order.getStatus().equals("SHIPPED")) {
            LocalDateTime estimatedDate = LocalDateTime.now().plusDays(1);
            return "Tomorrow (" + estimatedDate.toLocalDate().toString() + ")";
        }
        
        if (order.getStatus().equals("PROCESSING")) {
            LocalDateTime estimatedDate = LocalDateTime.now().plusDays(3);
            return "2-3 business days (by " + estimatedDate.toLocalDate().toString() + ")";
        }
        
        if (order.getStatus().equals("PAID")) {
            return "Payment confirmed. Order will be processed soon.";
        }
        
        return "Processing - estimated delivery will be available soon";
    }
    
    /**
     * Helper method to get current location
     */
    private String getCurrentLocation(Order order) {
        switch (order.getStatus()) {
            case "PAID":
            case "PROCESSING":
                return "VedaThrifts Warehouse - Nairobi";
            case "SHIPPED":
                return "In transit to " + order.getCounty() + " county";
            case "DELIVERED":
                return "Delivered to " + order.getShippingAddress() + ", " + order.getCity();
            case "CANCELLED":
                return "Order cancelled";
            default:
                return "Processing at VedaThrifts";
        }
    }
    
    /**
     * Helper method to generate order timeline
     */
    private List<Map<String, Object>> generateOrderTimeline(Order order) {
        List<Map<String, Object>> timeline = new java.util.ArrayList<>();
        
        // Order placed
        Map<String, Object> placed = new HashMap<>();
        placed.put("status", "Order Placed");
        placed.put("date", order.getCreatedAt().toString());
        placed.put("completed", true);
        placed.put("description", "Your order has been placed");
        placed.put("icon", "shopping-cart");
        timeline.add(placed);
        
        // Payment confirmed 
        if (order.getStatus().equals("PAID") || order.getStatus().equals("PROCESSING") || 
            order.getStatus().equals("SHIPPED") || order.getStatus().equals("DELIVERED")) {
            Map<String, Object> payment = new HashMap<>();
            payment.put("status", "Payment Confirmed");
            payment.put("date", order.getUpdatedAt() != null ? 
                order.getUpdatedAt().toString() : order.getCreatedAt().plusMinutes(5).toString());
            payment.put("completed", true);
            payment.put("description", "Payment received via M-PESA");
            payment.put("icon", "credit-card");
            timeline.add(payment);
        }
        
        // Processing
        boolean isProcessed = order.getStatus().equals("PROCESSING") || 
                              order.getStatus().equals("SHIPPED") || 
                              order.getStatus().equals("DELIVERED");
        Map<String, Object> processing = new HashMap<>();
        processing.put("status", "Processing");
        processing.put("date", isProcessed ? 
            order.getCreatedAt().plusHours(2).toString() : "Pending");
        processing.put("completed", isProcessed);
        processing.put("description", "Your order is being prepared at our warehouse");
        processing.put("icon", "package");
        timeline.add(processing);
        
        // Shipped
        boolean isShipped = order.getStatus().equals("SHIPPED") || order.getStatus().equals("DELIVERED");
        if (isShipped) {
            Map<String, Object> shipped = new HashMap<>();
            shipped.put("status", "Shipped");
            shipped.put("date", order.getCreatedAt().plusDays(1).toString());
            shipped.put("completed", true);
            shipped.put("description", "Your order has been shipped and is on its way");
            shipped.put("icon", "truck");
            timeline.add(shipped);
        } else {
            Map<String, Object> shipped = new HashMap<>();
            shipped.put("status", "Shipped");
            shipped.put("date", "Pending");
            shipped.put("completed", false);
            shipped.put("description", "Order will be shipped soon");
            shipped.put("icon", "truck");
            timeline.add(shipped);
        }
        
        // Delivered
        if (order.getStatus().equals("DELIVERED")) {
            Map<String, Object> delivered = new HashMap<>();
            delivered.put("status", "Delivered");
            delivered.put("date", order.getUpdatedAt().toString());
            delivered.put("completed", true);
            delivered.put("description", "Your order has been delivered");
            delivered.put("icon", "check-circle");
            timeline.add(delivered);
        } else {
            Map<String, Object> delivered = new HashMap<>();
            delivered.put("status", "Delivered");
            delivered.put("date", "Estimated: " + calculateEstimatedDelivery(order));
            delivered.put("completed", false);
            delivered.put("description", "Your order will be delivered soon");
            delivered.put("icon", "check-circle");
            timeline.add(delivered);
        }
        
        // Cancelled
        if (order.getStatus().equals("CANCELLED")) {
            Map<String, Object> cancelled = new HashMap<>();
            cancelled.put("status", "Cancelled");
            cancelled.put("date", order.getUpdatedAt().toString());
            cancelled.put("completed", false);
            cancelled.put("description", "Your order has been cancelled");
            cancelled.put("icon", "x-circle");
            timeline.add(cancelled);
        }
        
        return timeline;
    }
}