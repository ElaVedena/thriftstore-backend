package com.vedathrifts.service;

import com.vedathrifts.config.MpesaConfig;
import com.vedathrifts.dto.request.MpesaPaymentRequest;
import com.vedathrifts.dto.request.StkPushRequest;
import com.vedathrifts.dto.response.AccessTokenResponse;
import com.vedathrifts.dto.response.MpesaCallback;
import com.vedathrifts.dto.response.StkPushResponse;
import com.vedathrifts.model.Order;
import com.vedathrifts.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaService {

    private final MpesaConfig mpesaConfig;
    private final RestTemplate restTemplate;
    private final OrderRepository orderRepository;

    /**
     * Get OAuth access token from Safaricom
     */
    public String getAccessToken() {
        log.info("========== GETTING ACCESS TOKEN ==========");
        log.info("Environment: {}", mpesaConfig.isProduction() ? "PRODUCTION" : "SANDBOX");
        log.info("Base URL: {}", mpesaConfig.getBaseUrl());
        log.info("Consumer Key present: {}", mpesaConfig.getConsumerKey() != null && !mpesaConfig.getConsumerKey().isEmpty());
        
        try {
            String auth = mpesaConfig.getConsumerKey() + ":" + mpesaConfig.getConsumerSecret();
            byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + new String(encodedAuth);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authHeader);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            String url = mpesaConfig.getBaseUrl() + "/oauth/v1/generate?grant_type=client_credentials";
            log.info("Token URL: {}", url);

            ResponseEntity<AccessTokenResponse> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                AccessTokenResponse.class
            );

            if (response.getBody() != null) {
                log.info("✅ Access token obtained successfully");
                log.info("Expires in: {} seconds", response.getBody().getExpiresIn());
                return response.getBody().getAccessToken();
            }
        } catch (HttpClientErrorException e) {
            log.error("❌ HTTP Error getting access token: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.error("   This means your Consumer Key or Consumer Secret is incorrect!");
                log.error("   Check MPESA_CONSUMER_KEY and MPESA_CONSUMER_SECRET in Railway variables");
            }
        } catch (RestClientException e) {
            log.error("❌ RestClient Error getting access token: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error getting access token: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Generate STK push password
     * IMPORTANT: Password uses BusinessShortcode (Shortcode), NOT the till number
     */
    public String generatePassword() {
        String businessCode;
        String passkey;
        
        if (mpesaConfig.isSandbox()) {
            businessCode = "174379";
            passkey = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";
            log.debug("Using SANDBOX credentials for password generation");
        } else {
            // Password uses the BusinessShortcode (your Go Live shortcode)
            businessCode = mpesaConfig.getShortcode();
            passkey = mpesaConfig.getPasskey();
            log.debug("Using PRODUCTION credentials for password generation");
            log.debug("BusinessCode for password: {}", businessCode);
        }
        
        String timestamp = generateTimestamp();
        String data = businessCode + passkey + timestamp;
        String encoded = Base64.getEncoder().encodeToString(data.getBytes(StandardCharsets.UTF_8));
        log.debug("Generated password - BusinessCode: {}, Timestamp: {}", businessCode, timestamp);
        return encoded;
    }

    /**
     * Get the Business Shortcode (for STK push)
     * This is your Go Live shortcode, NOT the till number
     */
    public String getBusinessShortcode() {
        if (mpesaConfig.isSandbox()) {
            return "174379";
        }
        return mpesaConfig.getShortcode();
    }

    /**
     * Get the Till Number (PartyB)
     * This is where customers pay to
     */
    public String getTillNumber() {
        if (mpesaConfig.isSandbox()) {
            return "174379";
        }
        return mpesaConfig.getTillNumber();
    }

    /**
     * Get transaction type
     * For till payments, use CustomerBuyGoodsOnline
     */
    public String getTransactionType() {
        return "CustomerBuyGoodsOnline";
    }

    /**
     * Generate timestamp in format YYYYMMDDHHmmss
     */
    public String generateTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("Africa/Nairobi"));
        return sdf.format(new Date());
    }

    /**
     * Initiate STK Push
     */
    public StkPushResponse initiateStkPush(MpesaPaymentRequest request) {
        log.info("========== INITIATING STK PUSH ==========");
        log.info("Environment: {}", mpesaConfig.isProduction() ? "PRODUCTION" : "SANDBOX");
        log.info("Phone: {}, Amount: {}", request.getPhoneNumber(), request.getAmount());
        log.info("Account Reference: {}", request.getAccountReference());
        
        try {
            // Get access token
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.error("❌ Failed to get access token");
                throw new RuntimeException("Failed to get access token from Safaricom. Check your consumer key and secret.");
            }
            log.info("✅ Access token obtained successfully");

            // Format phone number
            String phone = formatPhoneNumber(request.getPhoneNumber());
            log.info("Formatted phone: {}", phone);
            
            // Generate password and timestamp
            String password = generatePassword();
            String timestamp = generateTimestamp();
            
            // Get the correct values
            String businessShortcode = getBusinessShortcode();  // Your Go Live shortcode
            String tillNumber = getTillNumber();  // Your till number (5435120)
            String transactionType = getTransactionType();  // CustomerBuyGoodsOnline
            
            log.info("=== M-PESA STK PUSH CONFIGURATION ===");
            log.info("Business Shortcode: {}", businessShortcode);
            log.info("Till Number (PartyB): {}", tillNumber);
            log.info("Transaction Type: {}", transactionType);
            log.info("Timestamp: {}", timestamp);
            log.info("=====================================");

            // Build STK push request
            StkPushRequest stkRequest = new StkPushRequest();
            stkRequest.setBusinessShortCode(businessShortcode);  // Your Go Live shortcode
            stkRequest.setPassword(password);
            stkRequest.setTimestamp(timestamp);
            stkRequest.setTransactionType(transactionType);  // CustomerBuyGoodsOnline
            stkRequest.setAmount(String.valueOf(request.getAmount().intValue()));
            stkRequest.setPartyA(phone);  // Customer's phone
            stkRequest.setPartyB(tillNumber);  // Your till number (5435120)
            stkRequest.setPhoneNumber(phone);
            stkRequest.setCallBackURL(mpesaConfig.getCallbackUrl());
            stkRequest.setAccountReference(request.getAccountReference());
            stkRequest.setTransactionDesc(request.getTransactionDesc());

            log.info("STK Push Request Details:");
            log.info("  BusinessShortCode: {}", stkRequest.getBusinessShortCode());
            log.info("  TransactionType: {}", stkRequest.getTransactionType());
            log.info("  Amount: {}", stkRequest.getAmount());
            log.info("  PartyA: {}", stkRequest.getPartyA());
            log.info("  PartyB: {}", stkRequest.getPartyB());
            log.info("  CallbackURL: {}", stkRequest.getCallBackURL());
            log.info("  AccountReference: {}", stkRequest.getAccountReference());

            // Make API call
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<StkPushRequest> entity = new HttpEntity<>(stkRequest, headers);

            String url = mpesaConfig.getBaseUrl() + "/mpesa/stkpush/v1/processrequest";
            log.info("STK Push URL: {}", url);

            ResponseEntity<StkPushResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                StkPushResponse.class
            );

            log.info("STK Push Response Status: {}", response.getStatusCode());
            
            if (response.getBody() != null) {
                StkPushResponse body = response.getBody();
                log.info("STK Push Response:");
                log.info("  ResponseCode: {}", body.getResponseCode());
                log.info("  ResponseDescription: {}", body.getResponseDescription());
                log.info("  CheckoutRequestID: {}", body.getCheckoutRequestID());
                log.info("  CustomerMessage: {}", body.getCustomerMessage());
                
                // Save checkoutRequestId to order if orderId is provided
                if (request.getOrderId() != null && body.getCheckoutRequestID() != null) {
                    orderRepository.findByOrderNumber(request.getOrderId())
                        .ifPresent(order -> {
                            order.setCheckoutRequestId(body.getCheckoutRequestID());
                            orderRepository.save(order);
                            log.info("✅ Saved checkoutRequestId for order: {}", request.getOrderId());
                        });
                }
                
                return body;
            } else {
                log.error("❌ Empty response from STK push");
                throw new RuntimeException("Empty response from M-Pesa");
            }

        } catch (HttpClientErrorException e) {
            log.error("❌ HTTP Error initiating STK push: {} - {}", 
                e.getStatusCode(), e.getResponseBodyAsString());
            
            String errorBody = e.getResponseBodyAsString();
            if (errorBody.contains("Merchant does not exist")) {
                log.error("   This means your BusinessShortcode or PartyB is incorrect!");
                log.error("   BusinessShortcode should be your Go Live shortcode");
                log.error("   PartyB should be your till number (5435120)");
                log.error("   Check MPESA_SHORTCODE and MPESA_TILL_NUMBER in Railway variables");
            }
            if (errorBody.contains("Wrong credentials")) {
                log.error("   This means your production credentials are incorrect!");
                log.error("   Check MPESA_CONSUMER_KEY and MPESA_CONSUMER_SECRET");
            }
            if (errorBody.contains("Invalid CallBackURL")) {
                log.error("   Your callback URL is invalid or not accessible!");
                log.error("   Make sure MPESA_CALLBACK_URL is set to a public HTTPS URL");
            }
            
            throw new RuntimeException("STK push failed with HTTP " + e.getStatusCode() + 
                ": " + errorBody);
        } catch (RestClientException e) {
            log.error("❌ RestClient Error initiating STK push: {}", e.getMessage(), e);
            throw new RuntimeException("STK push failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("❌ Unexpected error initiating STK push: {}", e.getMessage(), e);
            throw new RuntimeException("STK push failed: " + e.getMessage());
        }
    }

    /**
     * Handle M-Pesa callback
     */
    public void handleCallback(MpesaCallback callback) {
        log.info("========== PROCESSING M-PESA CALLBACK ==========");

        try {
            if (callback.getBody() == null || callback.getBody().getStkCallback() == null) {
                log.error("Invalid callback structure - missing body or stkCallback");
                return;
            }

            var stkCallback = callback.getBody().getStkCallback();
            String checkoutRequestId = stkCallback.getCheckoutRequestID();
            int resultCode = stkCallback.getResultCode();
            String resultDesc = stkCallback.getResultDesc();

            log.info("Callback Details:");
            log.info("  CheckoutRequestID: {}", checkoutRequestId);
            log.info("  ResultCode: {}", resultCode);
            log.info("  ResultDesc: {}", resultDesc);

            Order order = orderRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElse(null);
            
            if (order == null) {
                log.error("Order not found for CheckoutRequestID: {}", checkoutRequestId);
                return;
            }
            
            log.info("Found order: {} with status: {}", order.getOrderNumber(), order.getStatus());

            if (resultCode == 0) {
                log.info("✅ Payment successful for CheckoutRequestID: {}", checkoutRequestId);
                
                String receiptNumber = null;
                Double amount = null;
                String phoneNumber = null;

                if (stkCallback.getCallbackMetadata() != null && 
                    stkCallback.getCallbackMetadata().getItem() != null) {
                    
                    for (var item : stkCallback.getCallbackMetadata().getItem()) {
                        switch (item.getName()) {
                            case "MpesaReceiptNumber":
                                receiptNumber = (String) item.getValue();
                                break;
                            case "Amount":
                                amount = Double.parseDouble(item.getValue().toString());
                                break;
                            case "PhoneNumber":
                                phoneNumber = item.getValue().toString();
                                break;
                        }
                    }
                }

                order.setStatus("PAID");
                order.setMpesaReceiptNumber(receiptNumber);
                order.setPaymentCode(receiptNumber);
                orderRepository.save(order);
                log.info("✅ Order {} updated to PAID", order.getOrderNumber());

            } else {
                log.warn("❌ Payment failed for CheckoutRequestID: {}", checkoutRequestId);
                order.setStatus("PAYMENT_FAILED");
                order.setPaymentFailureReason(resultDesc);
                orderRepository.save(order);
                log.info("Order {} marked as PAYMENT_FAILED", order.getOrderNumber());
            }
        } catch (Exception e) {
            log.error("Error processing callback: {}", e.getMessage(), e);
        }
    }

    /**
     * Format phone number to 254XXXXXXXXX
     */
    private String formatPhoneNumber(String phone) {
        String cleaned = phone.replaceAll("[^0-9]", "");
        
        String formatted;
        if (cleaned.startsWith("0")) {
            formatted = "254" + cleaned.substring(1);
        } else if (cleaned.startsWith("7") || cleaned.startsWith("1")) {
            formatted = "254" + cleaned;
        } else if (cleaned.startsWith("254")) {
            formatted = cleaned;
        } else {
            formatted = "254" + cleaned;
        }
        
        return formatted;
    }
    
    private String maskString(String input) {
        if (input == null || input.length() < 8) {
            return "****";
        }
        return input.substring(0, 4) + "****" + input.substring(input.length() - 4);
    }
}