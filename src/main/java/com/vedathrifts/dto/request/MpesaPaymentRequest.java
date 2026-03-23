package com.vedathrifts.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MpesaPaymentRequest {
 @NotBlank(message = "Phone number is required")
 @Pattern(regexp = "^254[17]\\d{8}$", message = "Phone must be in format 2547XXXXXXXX")
 private String phoneNumber;
 
 @Min(value = 1, message = "Amount must be at least 1")
 private Double amount;
 
 private String orderId;
 
 private String accountReference = "VedaThrifts";
 
 private String transactionDesc = "Payment for order";
}
