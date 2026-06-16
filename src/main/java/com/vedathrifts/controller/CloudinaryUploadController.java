package com.vedathrifts.controller;

import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.service.CloudinaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/uploads")  
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class CloudinaryUploadController {

    @Autowired
    private CloudinaryService cloudinaryService;

    @PostMapping("/product-images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> uploadProductImages(@RequestParam("files") MultipartFile[] files) {
        try {
            System.out.println("=== CLOUDINARY UPLOAD ===");
            System.out.println("Number of files: " + files.length);
            
            List<String> imageUrls = new ArrayList<>();
            
            for (MultipartFile file : files) {
                System.out.println("Processing file: " + file.getOriginalFilename());
                
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "File is empty"));
                }
                
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "File must be an image"));
                }

                // FIX: Use 'limit' crop mode to preserve aspect ratio
                Map<String, Object> uploadResult = cloudinaryService.uploadImageWithTransformations(
                    file, 
                    "vedathrifts/products",
                    800,     // width - max width
                    800,     // height - max height
                    "limit", // FIXED: Use 'limit' instead of 'scale'
                    false    // removeBackground = false
                );
                
                String imageUrl = (String) uploadResult.get("secure_url");
                String publicId = (String) uploadResult.get("public_id");
                
                System.out.println("Uploaded to Cloudinary: " + imageUrl);
                System.out.println("Public ID: " + publicId);
                
                imageUrls.add(imageUrl);
            }
            
            System.out.println("All uploads successful. URLs: " + imageUrls);
            
            return ResponseEntity.ok(new ApiResponse(true, 
                "Images uploaded successfully", imageUrls));
                
        } catch (Exception e) {
            System.err.println("Upload failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Upload failed: " + e.getMessage()));
        }
    }

    @PostMapping("/review-images")
    @PreAuthorize("isAuthenticated()") 
    public ResponseEntity<?> uploadReviewImages(@RequestParam("files") MultipartFile[] files) {
        try {
            System.out.println("=== REVIEW IMAGES UPLOAD ===");
            System.out.println("Number of files: " + files.length);
            
            if (files.length > 5) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Maximum 5 images allowed for reviews"));
            }
            
            List<String> imageUrls = new ArrayList<>();
            
            for (MultipartFile file : files) {
                System.out.println("Processing file: " + file.getOriginalFilename());
                
                if (file.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "File is empty"));
                }
                
                if (file.getSize() > 5 * 1024 * 1024) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "File size must be less than 5MB"));
                }
                
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    return ResponseEntity.badRequest()
                            .body(new ApiResponse(false, "File must be an image"));
                }

                // FIX: Use 'limit' crop mode for reviews too
                Map<String, Object> uploadResult = cloudinaryService.uploadImageWithTransformations(
                    file, 
                    "vedathrifts/reviews",
                    400,     // width
                    400,     // height
                    "limit", // FIXED: Use 'limit' instead of 'scale'
                    false    // no background removal
                );
                
                String imageUrl = (String) uploadResult.get("secure_url");
                String publicId = (String) uploadResult.get("public_id");
                
                System.out.println("Uploaded to Cloudinary: " + imageUrl);
                System.out.println("Public ID: " + publicId);
                
                imageUrls.add(imageUrl);
            }
            
            System.out.println("All review images uploaded successfully. URLs: " + imageUrls);
            
            return ResponseEntity.ok(new ApiResponse(true, 
                "Review images uploaded successfully", imageUrls));
                
        } catch (Exception e) {
            System.err.println("Review images upload failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Review images upload failed: " + e.getMessage()));
        }
    }

    @DeleteMapping("/images")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteImage(@RequestParam String publicId) {
        try {
            System.out.println("Deleting image with public ID: " + publicId);
            
            Map<String, Object> result = cloudinaryService.deleteImage(publicId);
            
            return ResponseEntity.ok(new ApiResponse(true, 
                "Image deleted successfully", result));
                
        } catch (Exception e) {
            System.err.println("Delete failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500)
                    .body(new ApiResponse(false, "Delete failed: " + e.getMessage()));
        }
    }
}