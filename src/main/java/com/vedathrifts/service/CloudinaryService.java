package com.vedathrifts.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    // Original upload method (no transformations)
    public Map<String, Object> uploadImage(MultipartFile multipartFile, String folder) throws IOException {
        File fileToUpload = convertMultiPartToFile(multipartFile);
        
        try {
            System.out.println("Uploading to Cloudinary - Folder: " + folder);
            System.out.println("File size: " + multipartFile.getSize());
            
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image",
                "use_filename", true,
                "unique_filename", true
            );
            
            Map<String, Object> uploadResult = cloudinary.uploader().upload(fileToUpload, uploadParams);
            
            System.out.println("Upload successful!");
            System.out.println("Public ID: " + uploadResult.get("public_id"));
            System.out.println("URL: " + uploadResult.get("secure_url"));
            
            return uploadResult;
            
        } catch (IOException e) {
            System.err.println("Cloudinary upload failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Cloudinary upload failed: " + e.getMessage());
        } finally {
            if (fileToUpload.exists()) {
                fileToUpload.delete();
                System.out.println("Temp file deleted");
            }
        }
    }

    // NEW: Upload with transformations (resize + background removal)
    public Map<String, Object> uploadImageWithTransformations(MultipartFile multipartFile, 
                                                              String folder, 
                                                              int width, 
                                                              int height, 
                                                              String cropMode, 
                                                              boolean removeBackground) throws IOException {
        File fileToUpload = convertMultiPartToFile(multipartFile);
        
        try {
            System.out.println("Uploading to Cloudinary with transformations - Folder: " + folder);
            System.out.println("File size: " + multipartFile.getSize());
            System.out.println("Transformations: width=" + width + ", height=" + height + ", crop=" + cropMode + ", removeBackground=" + removeBackground);
            
            // Build transformation list
            List<Map<String, Object>> transformation = new ArrayList<>();
            
            // Step 1: Resize without cropping (scale mode)
            Map<String, Object> resizeParams = new HashMap<>();
            resizeParams.put("width", width);
            resizeParams.put("height", height);
            resizeParams.put("crop", cropMode); // "scale" = no cropping, "fill" = cropping
            transformation.add(resizeParams);
            
            // Step 2: Remove background (if requested)
            if (removeBackground) {
                Map<String, Object> bgRemovalParams = new HashMap<>();
                bgRemovalParams.put("effect", "background_removal");
                transformation.add(bgRemovalParams);
            }
            
            // Step 3: Optimize for web
            Map<String, Object> optimizeParams = new HashMap<>();
            optimizeParams.put("quality", "auto");
            optimizeParams.put("fetch_format", "auto"); // Converts to WebP if supported
            transformation.add(optimizeParams);
            
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "image",
                "use_filename", true,
                "unique_filename", true,
                "transformation", transformation
            );
            
            Map<String, Object> uploadResult = cloudinary.uploader().upload(fileToUpload, uploadParams);
            
            System.out.println("Upload successful!");
            System.out.println("Public ID: " + uploadResult.get("public_id"));
            System.out.println("URL: " + uploadResult.get("secure_url"));
            
            return uploadResult;
            
        } catch (IOException e) {
            System.err.println("Cloudinary upload failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Cloudinary upload failed: " + e.getMessage());
        } finally {
            if (fileToUpload.exists()) {
                fileToUpload.delete();
                System.out.println("Temp file deleted");
            }
        }
    }

    // Upload multiple images at once 
    public List<Map<String, Object>> uploadMultipleImages(List<MultipartFile> files, String folder) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                Map<String, Object> result = uploadImage(file, folder);
                results.add(result);
            } catch (IOException e) {
                System.err.println("Failed to upload file: " + file.getOriginalFilename());
            }
        }
        
        return results;
    }

    // Upload multiple and return just URLs
    public List<String> uploadImagesAndGetUrls(List<MultipartFile> files, String folder) throws IOException {
        List<String> urls = new ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                Map<String, Object> result = uploadImage(file, folder);
                urls.add((String) result.get("secure_url"));
            } catch (IOException e) {
                System.err.println("Failed to upload file: " + file.getOriginalFilename());
            }
        }
        
        return urls;
    }

    // Delete image
    public Map<String, Object> deleteImage(String publicId) throws IOException {
        try {
            System.out.println("Deleting image with public ID: " + publicId);
            
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            
            System.out.println("Delete result: " + result);
            return result;
            
        } catch (IOException e) {
            System.err.println("Cloudinary deletion failed: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Cloudinary deletion failed: " + e.getMessage());
        }
    }

    // Generate optimized URL with transformations (for existing images)
    public String generateOptimizedUrl(String publicId, int width, int height, boolean removeBackground) {
        Transformation transformation = new Transformation()
                .width(width)
                .height(height)
                .crop("scale")  // NO cropping
                .quality("auto")
                .fetchFormat("auto");
        
        if (removeBackground) {
            transformation.effect("background_removal");
        }
        
        return cloudinary.url()
                .transformation(transformation)
                .generate(publicId);
    }

    // Generate thumbnail URL (for product cards)
    public String generateThumbnailUrl(String publicId) {
        return cloudinary.url()
                .transformation(new Transformation()
                        .width(300)
                        .height(300)
                        .crop("scale")  // NO cropping
                        .quality("auto")
                        .fetchFormat("auto"))
                .generate(publicId);
    }

    // Generate mobile-friendly URL (for 3-column grid)
    public String generateMobileUrl(String publicId) {
        return cloudinary.url()
                .transformation(new Transformation()
                        .width(150)
                        .height(150)
                        .crop("scale")  
                        .quality("auto")
                        .fetchFormat("auto"))
                .generate(publicId);
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        String fileName = System.currentTimeMillis() + "_" + 
                Objects.requireNonNull(file.getOriginalFilename()).replaceAll("\\s+", "_");
        
        File convFile = new File(tempDir + File.separator + fileName);
        
        System.out.println("Creating temp file: " + convFile.getAbsolutePath());
        
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(file.getBytes());
        }
        
        return convFile;
    }
}