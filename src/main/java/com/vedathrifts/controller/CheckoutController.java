package com.vedathrifts.controller;

import com.vedathrifts.dto.request.CheckoutRequest;
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
import com.vedathrifts.security.UserDetailsImpl;
import com.vedathrifts.service.MpesaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@Slf4j
@RestController
@RequestMapping("/checkout")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CheckoutController {

    private final MpesaService mpesaService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @PostMapping("/initiate")
    public ResponseEntity<?> initiateCheckout(
            @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        
        try {
            log.info("========== INITIATING CHECKOUT ==========");
            log.info("User: {}", currentUser.getUsername());
            log.info("Phone: {}, Total: {}", request.getPhoneNumber(), request.getTotal());
            log.info("Items count: {}", request.getItems() != null ? request.getItems().size() : 0);
            
            // 1. Create order with PENDING_PAYMENT status
            User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found with id: " + currentUser.getId()));
            
            Order order = new Order();
            order.setUser(user);
            order.setOrderNumber(generateOrderNumber());
            order.setSubtotal(request.getSubtotal());
            order.setShippingCost(request.getShippingCost());
            order.setTotal(request.getTotal());
            order.setStatus("PENDING_PAYMENT");
            order.setPaymentMethod("M-PESA");
            order.setShippingAddress(request.getShippingAddress());
            order.setCity(request.getCity());
            order.setCounty(request.getCounty());
            order.setPhone(request.getPhone());
            
            log.info("Creating order: {}", order.getOrderNumber());
            
            // 2. Add items to the order
            if (request.getItems() != null && !request.getItems().isEmpty()) {
                for (CheckoutRequest.OrderItemRequest itemRequest : request.getItems()) {
                    OrderItem orderItem = new OrderItem();
                    
                    Product product = productRepository.findById(itemRequest.getProductId())
                        .orElse(null);
                    
                    if (product != null) {
                        orderItem.setProduct(product);
                        orderItem.setProductName(product.getName());
                    } else {
                        orderItem.setProductName(itemRequest.getProductName());
                    }
                    
                    orderItem.setQuantity(itemRequest.getQuantity());
                    orderItem.setPrice(itemRequest.getPrice());
                    orderItem.setOrder(order);
                    order.getItems().add(orderItem);
                }
                log.info("Added {} items to order", request.getItems().size());
            }
            
            // Save order
            Order savedOrder = orderRepository.save(order);
            log.info("Order created with ID: {}, OrderNumber: {}", savedOrder.getId(), savedOrder.getOrderNumber());

            // 3. Create MpesaPaymentRequest with ALL fields
            MpesaPaymentRequest mpesaRequest = new MpesaPaymentRequest();
            mpesaRequest.setPhoneNumber(request.getPhoneNumber());
            mpesaRequest.setAmount(request.getTotal());
            mpesaRequest.setOrderId(savedOrder.getOrderNumber());  // ← CRITICAL!
            mpesaRequest.setAccountReference("Order-" + savedOrder.getOrderNumber());
            mpesaRequest.setTransactionDesc("Payment for order " + savedOrder.getOrderNumber());

            log.info("M-Pesa Request Details:");
            log.info("  Phone: {}", mpesaRequest.getPhoneNumber());
            log.info("  Amount: {}", mpesaRequest.getAmount());
            log.info("  OrderId: {}", mpesaRequest.getOrderId());
            log.info("  AccountReference: {}", mpesaRequest.getAccountReference());

            // 4. Initiate STK push
            StkPushResponse stkResponse = mpesaService.initiateStkPush(mpesaRequest);

            if (stkResponse != null && "0".equals(stkResponse.getResponseCode())) {
                // Save checkout request ID
                savedOrder.setCheckoutRequestId(stkResponse.getCheckoutRequestID());
                orderRepository.save(savedOrder);
                
                log.info("✅ STK push initiated. CheckoutRequestID: {}", stkResponse.getCheckoutRequestID());
                
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("checkoutRequestId", stkResponse.getCheckoutRequestID());
                responseData.put("merchantRequestId", stkResponse.getMerchantRequestID());
                responseData.put("responseCode", stkResponse.getResponseCode());
                responseData.put("responseDescription", stkResponse.getResponseDescription());
                responseData.put("customerMessage", stkResponse.getCustomerMessage());
                responseData.put("orderNumber", savedOrder.getOrderNumber());
                
                return ResponseEntity.ok(new ApiResponse(true, 
                    "Payment initiated. Please check your phone to complete payment.", 
                    responseData));
            } else {
                String errorMsg = stkResponse != null ? 
                    stkResponse.getResponseDescription() : "No response from M-Pesa";
                log.error("❌ STK push failed: {}", errorMsg);
                orderRepository.delete(savedOrder);
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to initiate payment: " + errorMsg));
            }
            
        } catch (Exception e) {
            log.error("❌ Checkout failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Checkout failed: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{orderNumber}")
    public ResponseEntity<?> getOrderStatus(@PathVariable String orderNumber) {
        try {
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found"));
            
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderNumber", order.getOrderNumber());
            orderData.put("status", order.getStatus());
            orderData.put("mpesaReceiptNumber", order.getMpesaReceiptNumber());
            orderData.put("total", order.getTotal());
            
            return ResponseEntity.ok(new ApiResponse(true, "Order status retrieved", orderData));
        } catch (Exception e) {
            return ResponseEntity.status(404)
                .body(new ApiResponse(false, "Order not found"));
        }
    }

    @GetMapping("/checkout-status/{checkoutRequestId}")
    public ResponseEntity<?> getOrderStatusByCheckoutId(@PathVariable String checkoutRequestId) {
        try {
            Order order = orderRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
            
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderNumber", order.getOrderNumber());
            orderData.put("status", order.getStatus());
            orderData.put("mpesaReceiptNumber", order.getMpesaReceiptNumber());
            
            return ResponseEntity.ok(new ApiResponse(true, "Order found", orderData));
        } catch (Exception e) {
            return ResponseEntity.status(404)
                .body(new ApiResponse(false, "Order not found"));
        }
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }
}