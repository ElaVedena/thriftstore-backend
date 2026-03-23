package com.vedathrifts.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;
    
    private String description;
    
    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be positive")
    private Double price;
    
    private Double originalPrice;
    
    @NotNull(message = "Stock is required")
    @Min(value = 0, message = "Stock must be positive")
    private Integer stock;
    
    private String category;
    
    private String brand;
    
    private String condition;
    
    private String size;
    
    private String color;
    
    private String material;
    
    private String era;
    
    private List<String> images;
    
    private List<String> availableSizes;
    
    private String status;
}