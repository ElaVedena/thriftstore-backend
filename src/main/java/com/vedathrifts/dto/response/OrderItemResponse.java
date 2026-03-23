package com.vedathrifts.dto.response;

import lombok.Data;

@Data
public class OrderItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Double price;
    private Integer quantity;
    private String size;
    private String imageUrl;
    private Double subtotal;
    
    public static OrderItemResponse fromOrderItem(com.vedathrifts.model.OrderItem orderItem) {
        OrderItemResponse response = new OrderItemResponse();
        response.setId(orderItem.getId());
        
        if (orderItem.getProduct() != null) {
            response.setProductId(orderItem.getProduct().getId());
        }
        
        response.setProductName(orderItem.getProductName());
        response.setPrice(orderItem.getPrice());
        response.setQuantity(orderItem.getQuantity());
        response.setSize(orderItem.getSize());
        response.setImageUrl(orderItem.getImageUrl());
        response.setSubtotal(orderItem.getPrice() * orderItem.getQuantity());
        
        return response;
    }
}