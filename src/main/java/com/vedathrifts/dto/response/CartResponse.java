package com.vedathrifts.dto.response;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.vedathrifts.model.Cart;
import com.vedathrifts.model.CartItem;

@Data
public class CartResponse {
    private Long id;
    private Long userId;
    private List<CartItemResponse> items;
    private int itemCount;
    private double totalPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static CartResponse fromCart(Cart cart) {
        CartResponse response = new CartResponse();
        response.setId(cart.getId());
        response.setUserId(cart.getUser().getId());
        response.setItems(cart.getItems().stream()
                .map(CartItemResponse::fromCartItem)
                .collect(Collectors.toList()));
        response.setItemCount(cart.getItems().stream()
                .mapToInt(CartItem::getQuantity)
                .sum());
        response.setTotalPrice(cart.getItems().stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum());
        response.setCreatedAt(cart.getCreatedAt());
        response.setUpdatedAt(cart.getUpdatedAt());
        return response;
    }
}