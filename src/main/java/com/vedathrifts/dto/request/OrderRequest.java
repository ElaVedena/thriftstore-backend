package com.vedathrifts.dto.request;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class OrderRequest {
    @NotNull
    private Double subtotal;
    
    private Double shippingCost;
    
    @NotNull
    private Double total;
    
    @NotBlank
    private String paymentCode;
    
    private String paymentMethod;  
    
    @NotBlank
    private String shippingAddress;
    
    @NotBlank
    private String city;
    
    @NotBlank
    private String county;
    
    @NotBlank
    private String phone;
    
    @NotNull
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        private Long productId;
        private String productName;
        private Double price;
        private Integer quantity;
        private String size;
        private String imageUrl;
    }
}