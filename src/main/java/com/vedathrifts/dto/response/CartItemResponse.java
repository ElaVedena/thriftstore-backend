package com.vedathrifts.dto.response;

import com.vedathrifts.model.CartItem;

import lombok.Data;

@Data
public class CartItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Double price;
    private Integer quantity;
    private String size;
    private String imageUrl;
    
    public static CartItemResponse fromCartItem(CartItem item) {
        CartItemResponse response = new CartItemResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setProductName(item.getProductName());
        response.setPrice(item.getPrice());
        response.setQuantity(item.getQuantity());
        response.setSize(item.getSize());
        response.setImageUrl(item.getImageUrl());
        return response;
    }
}