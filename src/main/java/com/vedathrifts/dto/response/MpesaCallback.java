package com.vedathrifts.dto.response;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class MpesaCallback {
 @JsonProperty("Body")
 private CallbackBody body;
 
 @Data
 public static class CallbackBody {
     @JsonProperty("stkCallback")
     private StkCallback stkCallback;
 }
 
 @Data
 public static class StkCallback {
     @JsonProperty("MerchantRequestID")
     private String merchantRequestID;
     
     @JsonProperty("CheckoutRequestID")
     private String checkoutRequestID;
     
     @JsonProperty("ResultCode")
     private int resultCode;
     
     @JsonProperty("ResultDesc")
     private String resultDesc;
     
     @JsonProperty("CallbackMetadata")
     private CallbackMetadata callbackMetadata;
 }
 
 @Data
 public static class CallbackMetadata {
     @JsonProperty("Item")
     private List<CallbackItem> item;
 }
 
 @Data
 public static class CallbackItem {
     @JsonProperty("Name")
     private String name;
     
     @JsonProperty("Value")
     private Object value;
 }
}