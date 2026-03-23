package com.vedathrifts;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan({
    "com.vedathrifts.controller",
    "com.vedathrifts.config", 
    "com.vedathrifts.security",
    "com.vedathrifts.service",
    "com.vedathrifts.repository"
})
@SpringBootApplication
public class VedathriftsApplication {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        dotenv.entries().forEach(entry -> 
            System.setProperty(entry.getKey(), entry.getValue())
        );
        SpringApplication.run(VedathriftsApplication.class, args);
    }
}
