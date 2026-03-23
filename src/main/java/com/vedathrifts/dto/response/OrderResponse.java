package com.vedathrifts.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private Long userId;
    private String userName;
    private String userEmail;
    private List<OrderItemResponse> items;
    private Double subtotal;
    private Double shippingCost;
    private Double total;
    private String status;
    private String paymentMethod;
    private String paymentCode;
    private String shippingAddress;
    private String city;
    private String county;
    private String phone;
    private String mpesaReceiptNumber;
    private String checkoutRequestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private int itemCount;
    
    public static OrderResponse fromOrder(com.vedathrifts.model.Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        
        if (order.getUser() != null) {
            response.setUserId(order.getUser().getId());
            response.setUserName(order.getUser().getName());
            response.setUserEmail(order.getUser().getEmail());
        }
        
        if (order.getItems() != null) {
            response.setItems(order.getItems().stream()
                .map(OrderItemResponse::fromOrderItem)
                .collect(Collectors.toList()));
            response.setItemCount(order.getItems().size());
        }
        
        response.setSubtotal(order.getSubtotal());
        response.setShippingCost(order.getShippingCost());
        response.setTotal(order.getTotal());
        response.setStatus(order.getStatus());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setPaymentCode(order.getPaymentCode());
        response.setShippingAddress(order.getShippingAddress());
        response.setCity(order.getCity());
        response.setCounty(order.getCounty());
        response.setPhone(order.getPhone());
        response.setMpesaReceiptNumber(order.getMpesaReceiptNumber());
        response.setCheckoutRequestId(order.getCheckoutRequestId());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        
        return response;
    }
}
