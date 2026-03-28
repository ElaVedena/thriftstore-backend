package com.vedathrifts;

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
        // DEBUG: Print Railway MySQL environment variables
        System.out.println("=== DEBUG: Railway MySQL Variables ===");
        System.out.println("MYSQLHOST: " + System.getenv("MYSQLHOST"));
        System.out.println("MYSQLPORT: " + System.getenv("MYSQLPORT"));
        System.out.println("MYSQL_DATABASE: " + System.getenv("MYSQL_DATABASE"));
        System.out.println("MYSQLUSER: " + System.getenv("MYSQLUSER"));
        System.out.println("MYSQLPASSWORD: " + (System.getenv("MYSQLPASSWORD") != null ? "***SET***" : "NOT SET"));
        System.out.println("=====================================");
        
        if (java.nio.file.Files.exists(java.nio.file.Paths.get(".env"))) {
            try {
                io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.load();
                dotenv.entries().forEach(entry -> 
                    System.setProperty(entry.getKey(), entry.getValue())
                );
                System.out.println("✅ Loaded .env file for local development");
            } catch (Exception e) {
                System.out.println("⚠️ Could not load .env file: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ️ No .env file found. Using Railway environment variables.");
        }
        
        SpringApplication.run(VedathriftsApplication.class, args);
    }
}