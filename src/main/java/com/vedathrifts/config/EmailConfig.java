package com.vedathrifts.config;

import com.resend.Resend;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.vedathrifts.config.EmailProperties;

@Configuration
public class EmailConfig {
    
    private final EmailProperties emailProperties;

    public EmailConfig(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
    }

    @Bean
    public Resend resend() {
        return new Resend(emailProperties.getApiKey());
    }
}
