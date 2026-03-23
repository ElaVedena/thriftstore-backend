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
                    
                    // Find the product
                    Product product = productRepository.findById(itemRequest.getProductId())
                        .orElse(null);
                    
                    if (product != null) {
                        orderItem.setProduct(product);
                        // Also set the product name from the product if not provided
                        if (itemRequest.getProductName() == null) {
                            orderItem.setProductName(product.getName());
                        } else {
                            orderItem.setProductName(itemRequest.getProductName());
                        }
                    } else {
                        orderItem.setProductName(itemRequest.getProductName());
                    }
                    
                    orderItem.setQuantity(itemRequest.getQuantity());
                    orderItem.setPrice(itemRequest.getPrice());
                    orderItem.setOrder(order);
                    
                    // Add to order
                    order.getItems().add(orderItem);
                }
                log.info("Added {} items to order", request.getItems().size());
            } else {
                log.warn("No items in checkout request!");
            }
            
            // Save order with items (cascade will save items automatically)
            Order savedOrder = orderRepository.save(order);
            log.info("Order created with ID: {}, Items: {}", savedOrder.getId(), savedOrder.getItems().size());
            log.info("Order number value: {}", savedOrder.getOrderNumber());

            // 3. Create MpesaPaymentRequest object
            MpesaPaymentRequest mpesaRequest = new MpesaPaymentRequest();
            mpesaRequest.setPhoneNumber(request.getPhoneNumber());
            mpesaRequest.setAmount(request.getTotal());
            mpesaRequest.setAccountReference(savedOrder.getOrderNumber());
            mpesaRequest.setTransactionDesc("Payment for order " + savedOrder.getOrderNumber());

            log.info("Initiating STK push for order: {}", savedOrder.getOrderNumber());

            // 4. Initiate STK push
            StkPushResponse stkResponse = mpesaService.initiateStkPush(mpesaRequest);

            if (stkResponse != null && "0".equals(stkResponse.getResponseCode())) {
                // 5. Save checkout request ID to order
                savedOrder.setCheckoutRequestId(stkResponse.getCheckoutRequestID());
                orderRepository.save(savedOrder);
                
                log.info("STK push initiated successfully. CheckoutRequestID: {}", 
                    stkResponse.getCheckoutRequestID());
                
                // Prepare response with checkout details
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("checkoutRequestId", stkResponse.getCheckoutRequestID());
                responseData.put("merchantRequestId", stkResponse.getMerchantRequestID());
                responseData.put("responseCode", stkResponse.getResponseCode());
                responseData.put("responseDescription", stkResponse.getResponseDescription());
                responseData.put("customerMessage", stkResponse.getCustomerMessage());
                
                //Add order number to response
                log.info("Adding orderNumber to response: {}", savedOrder.getOrderNumber());
                responseData.put("orderNumber", savedOrder.getOrderNumber());
                
                // Verify it was added
                log.info("responseData contains orderNumber: {}", responseData.containsKey("orderNumber"));
                log.info("responseData keys: {}", responseData.keySet());
                log.info("Full responseData: {}", responseData);
                
                return ResponseEntity.ok(new ApiResponse(true, 
                    "Payment initiated. Please check your phone to complete payment.", 
                    responseData));
            } else {
                // Payment initiation failed 
                log.error("STK push failed: {}", stkResponse != null ? 
                    stkResponse.getResponseDescription() : "No response");
                orderRepository.delete(savedOrder);
                return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Failed to initiate payment. Please try again."));
            }
            
        } catch (Exception e) {
            log.error("Checkout failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Checkout failed: " + e.getMessage()));
        }
    }

    /**
     * Get order status by order number
     */
    @GetMapping("/status/{orderNumber}")
    public ResponseEntity<?> getOrderStatus(@PathVariable String orderNumber) {
        try {
            log.info("Fetching order status for order: {}", orderNumber);
            
            Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found with number: " + orderNumber));
            
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderNumber", order.getOrderNumber());
            orderData.put("status", order.getStatus());
            orderData.put("paymentMethod", order.getPaymentMethod());
            orderData.put("mpesaReceiptNumber", order.getMpesaReceiptNumber());
            orderData.put("total", order.getTotal());
            orderData.put("subtotal", order.getSubtotal());
            orderData.put("shippingCost", order.getShippingCost());
            orderData.put("checkoutRequestId", order.getCheckoutRequestId());
            orderData.put("createdAt", order.getCreatedAt());
            orderData.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);
            
            return ResponseEntity.ok(new ApiResponse(true, "Order status retrieved", orderData));
            
        } catch (Exception e) {
            log.error("Error fetching order status: {}", e.getMessage());
            return ResponseEntity.status(404)
                .body(new ApiResponse(false, "Order not found"));
        }
    }

    /**
     * Get order status by checkout request ID (useful for M-Pesa callbacks)
     */
    @GetMapping("/checkout-status/{checkoutRequestId}")
    public ResponseEntity<?> getOrderStatusByCheckoutId(@PathVariable String checkoutRequestId) {
        try {
            log.info("Fetching order for checkoutRequestId: {}", checkoutRequestId);
            
            Order order = orderRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new RuntimeException("Order not found for checkoutRequestId: " + checkoutRequestId));
            
            Map<String, Object> orderData = new HashMap<>();
            orderData.put("orderNumber", order.getOrderNumber());
            orderData.put("status", order.getStatus());
            orderData.put("paymentMethod", order.getPaymentMethod());
            orderData.put("mpesaReceiptNumber", order.getMpesaReceiptNumber());
            
            return ResponseEntity.ok(new ApiResponse(true, "Order found", orderData));
            
        } catch (Exception e) {
            log.error("Error fetching order by checkoutId: {}", e.getMessage());
            return ResponseEntity.status(404)
                .body(new ApiResponse(false, "Order not found for checkout ID: " + checkoutRequestId));
        }
    }

    /**
     * Generate unique order number
     */
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }
}