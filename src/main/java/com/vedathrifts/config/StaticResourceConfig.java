package com.vedathrifts.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
      
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        
        try {
            java.nio.file.Files.createDirectories(uploadPath);
            System.out.println("=== STATIC RESOURCE CONFIG ===");
            System.out.println("Upload directory configured: " + uploadPath);
            System.out.println("Directory exists: " + java.nio.file.Files.exists(uploadPath));
            System.out.println("Resource handler: /uploads/** -> file:" + uploadPath + "/");
        } catch (Exception e) {
            System.err.println("Failed to create upload directory: " + e.getMessage());
        }
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/")
                .setCachePeriod(3600);
    }
}