package com.vedathrifts.controller;

import com.vedathrifts.dto.request.ProfileUpdateRequest;
import com.vedathrifts.dto.response.ApiResponse;
import com.vedathrifts.model.User;
import com.vedathrifts.model.Role;
import com.vedathrifts.repository.UserRepository;
import com.vedathrifts.security.UserDetailsImpl;
import jakarta.validation.Valid;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Get current user's profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Remove sensitive information
        user.setPassword(null);
        
        return ResponseEntity.ok(user);
    }

    /**
     * Update current user's profile
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody ProfileUpdateRequest updateRequest,
                                           @AuthenticationPrincipal UserDetailsImpl currentUser) {
        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Update allowed fields
        if (updateRequest.getName() != null && !updateRequest.getName().isEmpty()) {
            user.setName(updateRequest.getName());
        }
        
        if (updateRequest.getPhone() != null) {
            user.setPhone(updateRequest.getPhone());
        }

        userRepository.save(user);

        // Remove sensitive information
        user.setPassword(null);

        return ResponseEntity.ok(new ApiResponse(true, "Profile updated successfully", user));
    }

    /**
     * Change current user's password
     */
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestParam String oldPassword,
                                            @RequestParam String newPassword,
                                            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        
        // Validate new password
        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "New password must be at least 6 characters long"));
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify old password
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Current password is incorrect"));
        }

        // Update password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(new ApiResponse(true, "Password changed successfully"));
    }

    /**
     * Get all users (Admin only)
     */
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllUsers(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) String search) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> users;

        if (search != null && !search.isEmpty()) {
            // Search by name or email
            users = userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable);
        } else if (role != null && !role.isEmpty()) {
            try {
                // Filter by role
                Role roleEnum = Role.valueOf(role.toUpperCase());
                users = userRepository.findByRole(roleEnum, pageable);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse(false, "Invalid role: " + role));
            }
        } else {
            // Get all users
            users = userRepository.findAll(pageable);
        }

        // Remove passwords from response
        users.forEach(user -> user.setPassword(null));

        return ResponseEntity.ok(users);
    }

    /**
     * Get user by ID (Admin only)
     */
    @GetMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // Remove sensitive information
        user.setPassword(null);

        return ResponseEntity.ok(user);
    }

    /**
     * Update user role (Admin only)
     */
    @PutMapping("/admin/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long userId,
                                            @RequestParam String role) {
        
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Role newRole = Role.valueOf(role.toUpperCase());
            user.setRole(newRole);
            userRepository.save(user);
            
            user.setPassword(null);
            
            return ResponseEntity.ok(new ApiResponse(true, "User role updated to " + newRole, user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse(false, "Invalid role: " + role));
        }
    }

    /**
     * Toggle user active status (Admin only)
     */
    @PutMapping("/admin/{userId}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleUserStatus(@PathVariable Long userId) {
        
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }


        return ResponseEntity.ok(new ApiResponse(true, "User status toggled successfully"));
    }

    /**
     * Delete user (Admin only) - Soft delete recommended
     */
    @DeleteMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long userId) {
        
        User user = userRepository.findById(userId)
                .orElse(null);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        userRepository.delete(user);

        return ResponseEntity.ok(new ApiResponse(true, "User deleted successfully"));
    }

    /**
     * Get user statistics (Admin only)
     */
    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserStats() {
        
        long totalUsers = userRepository.count();
        long adminCount = userRepository.countByRole(Role.ADMIN);
        long userCount = userRepository.countByRole(Role.USER);


        return ResponseEntity.ok(new ApiResponse(true, "User statistics", 
            Map.of(
                "totalUsers", totalUsers,
                "admins", adminCount,
                "regularUsers", userCount
                
            )));
    }
}