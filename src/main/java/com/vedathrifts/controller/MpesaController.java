package com.vedathrifts.controller;

import com.vedathrifts.dto.request.MpesaPaymentRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.dto.response.StkPushResponse;
import com.vedathrifts.model.Order;
import com.vedathrifts.model.OrderItem;
import com.vedathrifts.model.Product;
import com.vedathrifts.model.User;
import com.vedathrifts.repository.OrderRepository;
import com.vedathrifts.repository.ProductRepository;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.service.MpesaService;
import com.vedathrifts.service.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@RestController
@RequestMapping("/mpesa")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class MpesaController {

    private final MpesaService mpesaService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * Initiate STK Push payment
     */
    @PostMapping("/stkpush")
    public ResponseEntity<?> initiatePayment(@Valid @RequestBody MpesaPaymentRequest request) {
        log.info("========== STK PUSH REQUEST ==========");
        log.info("Phone: {}, Amount: {}", request.getPhoneNumber(), request.getAmount());
        log.info("Order Data - OrderId: {}, AccountRef: {}", request.getOrderId(), request.getAccountReference());
        
        try {
            StkPushResponse response = mpesaService.initiateStkPush(request);
            
            log.info("STK Push Response Code: {}", response != null ? response.getResponseCode() : "null");
            log.info("STK Push CheckoutRequestID: {}", response != null ? response.getCheckoutRequestID() : "null");
            
            if (response != null && "0".equals(response.getResponseCode())) {
                
                if (request.getOrderId() != null) {
                    Order order = orderRepository.findByOrderNumber(request.getOrderId())
                            .orElse(null);
                    if (order != null) {
                        order.setCheckoutRequestId(response.getCheckoutRequestID());
                        orderRepository.save(order);
                        log.info("Updated order {} with checkoutRequestId: {}", 
                            request.getOrderId(), response.getCheckoutRequestID());
                    }
                }
                
                return ResponseEntity.ok(new ApiResponse(true, 
                    "STK push sent successfully. Check your phone for the M-Pesa prompt.", response));
            } else {
                String message = response != null ? response.getResponseDescription() : "No response from M-Pesa";
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, message));
            }

        } catch (Exception e) {
            log.error("STK push failed with exception: ", e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Payment initiation failed: " + e.getMessage()));
        }
    }

    /**
     * Callback endpoint for M-Pesa results
     */
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<?> handleCallback(@RequestBody Map<String, Object> callback) {
        log.info("========== M-PESA CALLBACK RECEIVED ==========");
        log.info("Callback payload: {}", callback);

        try {
            // Extract the callback data
            Map<String, Object> body = (Map<String, Object>) callback.get("Body");
            if (body == null) {
                log.error("Invalid callback: missing Body");
                return ResponseEntity.ok().body("{\"ResultCode\":0,\"ResultDesc\":\"Success\"}");
            }

            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");
            if (stkCallback == null) {
                log.error("Invalid callback: missing stkCallback");
                return ResponseEntity.ok().body("{\"ResultCode\":0,\"ResultDesc\":\"Success\"}");
            }

            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            Integer resultCode = (Integer) stkCallback.get("ResultCode");
            String resultDesc = (String) stkCallback.get("ResultDesc");

            log.info("CheckoutRequestID: {}", checkoutRequestId);
            log.info("ResultCode: {}", resultCode);
            log.info("ResultDesc: {}", resultDesc);

            // Find the order by checkoutRequestId
            Order order = orderRepository.findByCheckoutRequestId(checkoutRequestId)
                    .orElse(null);

            if (order == null) {
                log.error("Order not found for checkoutRequestId: {}", checkoutRequestId);
                return ResponseEntity.ok().body("{\"ResultCode\":0,\"ResultDesc\":\"Success\"}");
            }

            log.info("Found order: {} with current status: {}", order.getOrderNumber(), order.getStatus());

            if (resultCode == 0) {
                // Payment successful 
                Map<String, Object> callbackMetadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
                if (callbackMetadata != null) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) callbackMetadata.get("Item");
                    
                    String receiptNumber = null;
                    Double amount = null;
                    String phoneNumber = null;
                    String transactionDate = null;
                    
                    if (items != null) {
                        for (Map<String, Object> item : items) {
                            String name = (String) item.get("Name");
                            Object value = item.get("Value");
                            
                            switch (name) {
                                case "MpesaReceiptNumber":
                                    receiptNumber = (String) value;
                                    break;
                                case "Amount":
                                    amount = Double.parseDouble(value.toString());
                                    break;
                                case "PhoneNumber":
                                    phoneNumber = value.toString();
                                    break;
                                case "TransactionDate":
                                    transactionDate = value.toString();
                                    break;
                            }
                        }
                    }
                    
                    log.info("Payment successful! Receipt: {}, Amount: {}, Phone: {}", 
                        receiptNumber, amount, phoneNumber);
                    
                    // Update order status to PAID
                    order.setStatus("PAID");
                    order.setMpesaReceiptNumber(receiptNumber);
                    order.setPaymentCode(receiptNumber);
                    order.setMpesaTransactionDate(transactionDate);
                    order.setUpdatedAt(LocalDateTime.now());
                    
                    //UPDATE PRODUCT STOCK 
                    if (order.getItems() != null && !order.getItems().isEmpty()) {
                        log.info("Updating stock for {} items in order", order.getItems().size());
                        
                        for (OrderItem orderItem : order.getItems()) {
                            Product product = orderItem.getProduct();
                            
                            if (product != null) {
                                int currentStock = product.getStock();
                                int orderedQuantity = orderItem.getQuantity();
                                int newStock = currentStock - orderedQuantity;
                                
                                newStock = Math.max(0, newStock);
                                product.setStock(newStock);
                                productRepository.save(product);
                                
                                log.info("✅ Updated stock for product {}: {} -> {}", 
                                    product.getName(), currentStock, newStock);
                            }
                        }
                    }
                    
                    // Save the updated order
                    Order savedOrder = orderRepository.save(order);
                    log.info("✅ Order {} updated to PAID", savedOrder.getOrderNumber());
                    
                    //SEND CONFIRMATION EMAIL AFTER PAYMENT
                    try {
                        User user = savedOrder.getUser();
                        log.info("📧 Sending confirmation email for order: {} to: {}", 
                            savedOrder.getOrderNumber(), user.getEmail());
                        
                        emailService.sendOrderConfirmationEmail(savedOrder, user);
                        log.info("✅ Confirmation email sent to {}", user.getEmail());
                    } catch (Exception e) {
                        log.error("❌ Failed to send confirmation email: {}", e.getMessage(), e);
                    }
                }
            } else {
                // Payment failed
                log.warn("❌ Payment failed: {} - {}", resultCode, resultDesc);
                order.setStatus("PAYMENT_FAILED");
                order.setPaymentFailureReason(resultDesc);
                order.setUpdatedAt(LocalDateTime.now());
                orderRepository.save(order);
                log.info("Order {} marked as PAYMENT_FAILED", order.getOrderNumber());
            }

            // Always return success to Safaricom
            Map<String, Object> response = new HashMap<>();
            response.put("ResultCode", 0);
            response.put("ResultDesc", "Success");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing callback: ", e);
            Map<String, Object> response = new HashMap<>();
            response.put("ResultCode", 0);
            response.put("ResultDesc", "Success");
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Endpoint to manually trigger email for testing
     */
    @PostMapping("/send-test-email/{orderId}")
    public ResponseEntity<?> sendTestEmail(@PathVariable Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            
            emailService.sendOrderConfirmationEmail(order, order.getUser());
            
            return ResponseEntity.ok(new ApiResponse(true, "Test email sent to " + order.getUser().getEmail()));
        } catch (Exception e) {
            log.error("Failed to send test email: ", e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed: " + e.getMessage()));
        }
    }

    /**
     * Endpoint to check order status by checkoutRequestId
     */
    @GetMapping("/order-status/{checkoutRequestId}")
    public ResponseEntity<?> getOrderStatusByCheckoutId(@PathVariable String checkoutRequestId) {
        try {
            Order order = orderRepository.findByCheckoutRequestId(checkoutRequestId)
                    .orElse(null);
            
            if (order == null) {
                return ResponseEntity.notFound().build();
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("orderNumber", order.getOrderNumber());
            response.put("status", order.getStatus());
            response.put("mpesaReceipt", order.getMpesaReceiptNumber());
            
            return ResponseEntity.ok(new ApiResponse(true, "Order status retrieved", response));
        } catch (Exception e) {
            log.error("Failed to get order status: ", e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed: " + e.getMessage()));
        }
    }

    /**
     * Test endpoint to check if controller is reachable
     */
    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        log.info("Ping endpoint called");
        return ResponseEntity.ok(new ApiResponse(true, "M-Pesa controller is working"));
    }

    /**
     * Test endpoint to check if service is running
     */
    @GetMapping("/test")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok(new ApiResponse(true, "M-Pesa service is running"));
    }

    /**
     * Get access token (for testing)
     */
    @GetMapping("/token")
    public ResponseEntity<?> getToken() {
        log.info("========== GET TOKEN REQUEST ==========");
        try {
            String token = mpesaService.getAccessToken();
            if (token != null) {
                log.info("Token obtained successfully");
                return ResponseEntity.ok(new ApiResponse(true, "Token obtained", token));
            } else {
                log.error("Failed to get token");
                return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Failed to get token"));
            }
        } catch (Exception e) {
            log.error("Error getting token: ", e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
}