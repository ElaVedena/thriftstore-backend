package com.vedathrifts.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "mpesa")
public class MpesaConfig {
    private String consumerKey;
    private String consumerSecret;
    private String passkey;
    private String shortcode;
    private String tillNumber;
    private String environment;
    private String baseUrl;
    private String callbackUrl;
    
    @PostConstruct
    public void validateAndLog() {
        log.info("========== M-PESA CONFIGURATION LOADED ==========");
        
        // Set default environment if not specified
        if (environment == null || environment.isEmpty()) {
            environment = "sandbox";
            log.warn("⚠️ Environment not set, defaulting to: {}", environment);
        }
        
        // Set default base URL if not specified
        if (baseUrl == null || baseUrl.isEmpty()) {
            if (isProduction()) {
                baseUrl = "https://api.safaricom.co.ke";
            } else {
                baseUrl = "https://sandbox.safaricom.co.ke";
            }
            log.warn("⚠️ Base URL not set, defaulting to: {}", baseUrl);
        }
        
        log.info("Environment: {}", environment);
        log.info("Base URL: {}", baseUrl);
        log.info("Shortcode (PayBill): {}", shortcode != null ? shortcode : "NOT SET");
        log.info("Till Number (Buy Goods): {}", tillNumber != null ? tillNumber : "NOT SET");
        log.info("Callback URL: {}", callbackUrl);
        log.info("Consumer Key: {}", maskString(consumerKey));
        log.info("Consumer Secret: {}", maskString(consumerSecret));
        log.info("Passkey: {}", maskString(passkey));
        log.info("================================================");
        
        // Validate required fields
        if (consumerKey == null || consumerKey.isEmpty()) {
            log.error("❌ MPESA_CONSUMER_KEY is not set!");
        }
        if (consumerSecret == null || consumerSecret.isEmpty()) {
            log.error("❌ MPESA_CONSUMER_SECRET is not set!");
        }
        if (passkey == null || passkey.isEmpty()) {
            log.error("❌ MPESA_PASSKEY is not set!");
        }
        
        if (isProduction()) {
            if ((shortcode == null || shortcode.isEmpty()) && (tillNumber == null || tillNumber.isEmpty())) {
                log.error("❌ Neither MPESA_SHORTCODE nor MPESA_TILL_NUMBER is set!");
            } else if (tillNumber != null && !tillNumber.isEmpty()) {
                log.info("✅ Using Till Number: {} for Buy Goods (CustomerBuyGoodsOnline)", tillNumber);
            } else if (shortcode != null && !shortcode.isEmpty()) {
                log.info("✅ Using Shortcode: {} for PayBill (CustomerPayBillOnline)", shortcode);
            }
        }
        
        if (callbackUrl == null || callbackUrl.isEmpty()) {
            log.warn("⚠️ MPESA_CALLBACK_URL is not set!");
        }
        
        // Log environment-specific info
        if (isSandbox()) {
            log.info("🔧 Running in SANDBOX mode - using test credentials");
            log.info("   Test phone number: 254708374149");
            log.info("   Test PIN: 123456");
            log.info("   Test shortcode: 174379");
        } else if (isProduction()) {
            log.info("🚀 Running in PRODUCTION mode - LIVE transactions will be processed");
            log.info("   ⚠️ Make sure you have sufficient funds in your till/paybill");
        } else {
            log.warn("⚠️ Environment not set to 'sandbox' or 'production' - using: {}", environment);
        }
    }
    
    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(environment);
    }
    
    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment);
    }
    
    public String getBusinessCode() {
        if (isSandbox()) {
            return "174379";
        }
        if (tillNumber != null && !tillNumber.isEmpty()) {
            return tillNumber;
        }
        return shortcode;
    }
    
    public String getTransactionType() {
        if (isSandbox()) {
            return "CustomerBuyGoodsOnline";
        }
        if (tillNumber != null && !tillNumber.isEmpty()) {
            return "CustomerBuyGoodsOnline";
        }
        return "CustomerPayBillOnline";
    }
    
    public String getCallbackUrl() {
        return callbackUrl;
    }
    
    private String maskString(String input) {
        if (input == null || input.isEmpty()) {
            return "NOT SET";
        }
        if (input.length() <= 8) {
            return "***";
        }
        return input.substring(0, 4) + "****" + input.substring(input.length() - 4);
    }
}