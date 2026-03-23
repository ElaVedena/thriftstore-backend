package com.vedathrifts.dto.response;

import com.vedathrifts.model.WishlistItem;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WishlistItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Double price;
    private String imageUrl;
    
    public WishlistItemResponse(Long id, Long productId, String productName, Double price, String imageUrl) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.imageUrl = imageUrl;
    }
    
    // Static factory method to create from entity
    public static WishlistItemResponse fromEntity(WishlistItem item) {
        if (item == null) return null;
        
        return new WishlistItemResponse(
            item.getId(),
            item.getProductId(),
            item.getProductName(),
            item.getPrice(),
            item.getImageUrl()
        );
    }
}