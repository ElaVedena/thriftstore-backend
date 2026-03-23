package com.vedathrifts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "resend")
public class EmailProperties {
    private String apiKey;
    private String fromEmail;
    private String fromName;
    private String adminEmail;
}