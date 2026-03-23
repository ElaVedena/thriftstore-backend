package com.vedathrifts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @NotBlank
    private String name;
    
    @Pattern(regexp = "^0[17]\\d{8}$", message = "Invalid Kenyan phone number")
    private String phone;
}
