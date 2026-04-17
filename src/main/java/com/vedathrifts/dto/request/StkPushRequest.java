package com.vedathrifts.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StkPushRequest {
    @JsonProperty("BusinessShortCode")
    private String businessShortCode;
    
    @JsonProperty("Password")
    private String password;
    
    @JsonProperty("Timestamp")
    private String timestamp;
    
    @JsonProperty("TransactionType")
    private String transactionType = "CustomerBuyGoodsOnline";  // Changed from CustomerPayBillOnline to CustomerBuyGoodsOnline for Till Number
    
    @JsonProperty("Amount")
    private String amount;
    
    @JsonProperty("PartyA")
    private String partyA;
    
    @JsonProperty("PartyB")
    private String partyB;
    
    @JsonProperty("PhoneNumber")
    private String phoneNumber;
    
    @JsonProperty("CallBackURL")
    private String callBackURL;
    
    @JsonProperty("AccountReference")
    private String accountReference;
    
    @JsonProperty("TransactionDesc")
    private String transactionDesc;
}