package com.vedathrifts.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CartItemRequest {
    @NotNull(message = "Product ID is required")
    private Long productId;
    
    private String productName;
    
    @Min(value = 0, message = "Price must be positive")
    private Double price;
    
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity = 1;
    
    private String size;
    
    private String imageUrl;
}