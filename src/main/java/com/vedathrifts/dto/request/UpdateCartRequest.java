package com.vedathrifts.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateCartRequest {
    @NotNull
    private Long productId;
    
    private String size;
    
    @NotNull
    private Integer quantity;
}
