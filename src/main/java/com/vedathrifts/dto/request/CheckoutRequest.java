package com.vedathrifts.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class CheckoutRequest {
    private String phoneNumber;
    private Double subtotal;
    private Double shippingCost;
    private Double total;
    private String shippingAddress;
    private String city;
    private String county;
    private String phone;
    private List<OrderItemRequest> items;

    @Data
    public static class OrderItemRequest {
        private Long productId;
        private String productName;
        private Integer quantity;
        private Double price;
    }
}