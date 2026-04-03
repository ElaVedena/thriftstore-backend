package com.vedathrifts.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.vedathrifts.config.EmailProperties;
import com.vedathrifts.dto.request.ContactRequest;
import com.vedathrifts.model.Order;
import com.vedathrifts.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailProperties emailProperties;
    private final TemplateEngine templateEngine;
    private Resend resend;
    
    private static final String BASE_URL = "https://vedathrifts.com";

    @PostConstruct
    public void init() {
        log.info("=================================");
        log.info("EMAIL SERVICE INITIALIZING...");
        log.info("=================================");
        
        try {
            log.info("EmailProperties injection: {}", emailProperties != null ? "SUCCESS" : "FAILED");
            
            if (emailProperties != null) {
                log.info("Configuration loaded:");
                log.info("  - From email: {}", emailProperties.getFromEmail() != null ? emailProperties.getFromEmail() : "NOT SET");
                log.info("  - From name: {}", emailProperties.getFromName() != null ? emailProperties.getFromName() : "NOT SET");
                log.info("  - Admin email: {}", emailProperties.getAdminEmail() != null ? emailProperties.getAdminEmail() : "NOT SET");
                log.info("  - API key present: {}", emailProperties.getApiKey() != null ? "YES" : "NO");
                
                if (emailProperties.getApiKey() != null) {
                    log.info("  - API key length: {}", emailProperties.getApiKey().length());
                    log.info("  - API key starts with: {}...", 
                        emailProperties.getApiKey().substring(0, Math.min(8, emailProperties.getApiKey().length())));
                    
                    try {
                        Resend testClient = new Resend(emailProperties.getApiKey());
                        log.info("Resend client test initialization successful");
                    } catch (Exception e) {
                        log.error("Resend client test initialization failed: {}", e.getMessage());
                    }
                } else {
                    log.error("API KEY IS MISSING! Check application.properties");
                    log.error("Make sure 'resend.api-key' is set in application.properties");
                }
                
                log.info("TemplateEngine injection: {}", templateEngine != null ? "SUCCESS" : "FAILED");
            }
        } catch (Exception e) {
            log.error("Error during EmailService initialization: {}", e.getMessage(), e);
        }
        
        log.info("=================================");
        log.info("Email Service initialization complete");
        log.info("=================================");
    }

    private Resend getResendClient() {
        if (resend == null) {
            log.info("Initializing Resend client...");
            log.info("API Key present: {}", emailProperties.getApiKey() != null);
            log.info("API Key length: {}", emailProperties.getApiKey() != null ? emailProperties.getApiKey().length() : 0);
            
            if (emailProperties.getApiKey() == null || emailProperties.getApiKey().isEmpty()) {
                log.error("API KEY IS NULL OR EMPTY!");
                return null;
            }
            
            try {
                resend = new Resend(emailProperties.getApiKey());
                log.info("Resend client initialized successfully");
            } catch (Exception e) {
                log.error("Failed to initialize Resend client: {}", e.getMessage(), e);
                return null;
            }
        }
        return resend;
    }

    // Send contact form email
    public void sendContactEmail(ContactRequest request) {
        log.info("========== CONTACT EMAIL CALLED ==========");
        log.info("Contact from: {} ({})", request.getName(), request.getEmail());
        
        try {
            Resend client = getResendClient();
            if (client == null) {
                log.error("Resend client is null - cannot send email");
                return;
            }
            
            // 1. Send email to admin
            String adminSubject = "New Contact Message from " + request.getName();
            String adminHtml = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #CEABB1 0%, #b88e96 100%); color: white; padding: 30px; text-align: center; border-radius: 12px 12px 0 0; }
                        .content { padding: 30px; background: #f9f9f9; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 12px 12px; }
                        .info-box { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #CEABB1; }
                        .message-box { background: #f5f5f5; padding: 20px; border-radius: 8px; margin: 20px 0; font-style: italic; }
                        .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                        .label { font-weight: bold; color: #CEABB1; width: 80px; display: inline-block; }
                        .reply-btn { display: inline-block; padding: 10px 20px; background: #CEABB1; color: white; text-decoration: none; border-radius: 6px; margin-top: 15px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>New Contact Form Submission</h2>
                        </div>
                        <div class="content">
                            <p><strong>You have received a new message from your website contact form.</strong></p>
                            
                            <div class="info-box">
                                <p><span class="label">Name:</span> %s</p>
                                <p><span class="label">Email:</span> <a href="mailto:%s">%s</a></p>
                                <p><span class="label">Date:</span> %s</p>
                            </div>
                            
                            <div class="message-box">
                                <strong>Message:</strong><br><br>
                                %s
                            </div>
                            
                            <p>You can reply directly to this email to respond to the customer.</p>
                            
                            <a href="mailto:%s" class="reply-btn">Reply to Customer</a>
                        </div>
                        <div class="footer">
                            <p>VedaThrifts Contact System</p>
                        </div>
                    </div>
                </body>
                </html>
                """, 
                request.getName(),
                request.getEmail(), request.getEmail(),
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")),
                request.getMessage().replace("\n", "<br>"),
                request.getEmail()
            );
            
            CreateEmailOptions adminEmail = CreateEmailOptions.builder()
                .from(emailProperties.getFromName() + " <" + emailProperties.getFromEmail() + ">")
                .to(emailProperties.getAdminEmail() != null ? emailProperties.getAdminEmail() : emailProperties.getFromEmail())
                .subject(adminSubject)
                .html(adminHtml)
                .build();
            
            CreateEmailResponse adminResponse = client.emails().send(adminEmail);
            log.info("Contact email sent to admin. ID: {}", adminResponse.getId());
            
            // 2. Send auto-reply to customer
            String customerSubject = "Thank you for contacting VedaThrifts";
            String customerHtml = String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                        .header { background: linear-gradient(135deg, #CEABB1 0%, #b88e96 100%); color: white; padding: 30px; text-align: center; border-radius: 12px 12px 0 0; }
                        .content { padding: 30px; background: #f9f9f9; border: 1px solid #e0e0e0; border-top: none; border-radius: 0 0 12px 12px; }
                        .message-preview { background: white; padding: 20px; border-radius: 8px; margin: 20px 0; border-left: 4px solid #CEABB1; font-style: italic; }
                        .button { display: inline-block; padding: 12px 24px; background: #CEABB1; color: white; text-decoration: none; border-radius: 8px; margin-top: 20px; }
                        .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; border-top: 1px solid #e0e0e0; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>Thank You for Reaching Out!</h2>
                        </div>
                        <div class="content">
                            <p>Hi <strong>%s</strong>,</p>
                            <p>Thank you for contacting <strong>VedaThrifts</strong>! We've received your message and will get back to you within <strong>24 hours</strong>.</p>
                            
                            <div class="message-preview">
                                <p><strong>Your message:</strong></p>
                                <p style="color: #666;">"%s"</p>
                            </div>
                            
                            <p><strong>In the meantime, you can:</strong></p>
                            <ul>
                                <li>Browse our latest <a href="%s/shop" style="color: #CEABB1;">thrift collection</a></li>
                                <li>Follow us on <a href="https://www.instagram.com/vashvedena" style="color: #CEABB1;">Instagram</a> for daily updates</li>
                                <li>Check out our <a href="%s/faq" style="color: #CEABB1;">FAQ page</a> for quick answers</li>
                            </ul>
                            
                            <a href="%s/shop" class="button">Start Shopping</a>
                            
                            <p style="margin-top: 20px;">Warm regards,<br><strong>The VedaThrifts Team</strong></p>
                        </div>
                        <div class="footer">
                            <p>© 2026 VedaThrifts. All rights reserved.</p>
                            <p style="font-size: 11px;">You received this email because you contacted us through our website.</p>
                        </div>
                    </div>
                </body>
                </html>
                """, 
                request.getName(),
                request.getMessage().replace("\n", "<br>"),
                BASE_URL, BASE_URL, BASE_URL
            );
            
            CreateEmailOptions customerEmail = CreateEmailOptions.builder()
                .from(emailProperties.getFromName() + " <" + emailProperties.getFromEmail() + ">")
                .to(request.getEmail())
                .subject(customerSubject)
                .html(customerHtml)
                .build();
            
            CreateEmailResponse customerResponse = client.emails().send(customerEmail);
            log.info("Auto-reply sent to customer. ID: {}", customerResponse.getId());
            
        } catch (ResendException e) {
            log.error("Resend API exception: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send contact email: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    public void sendWelcomeEmail(User user) {
        log.info("========== WELCOME EMAIL CALLED ==========");
        log.info("Method entered for user: {}", user != null ? user.getEmail() : "null user");
        
        if (user == null) {
            log.error("User is null!");
            return;
        }
        
        log.info("Sending welcome email to: {}", user.getEmail());
        log.info("User details - ID: {}, Name: {}, Email: {}", 
            user.getId(), user.getName(), user.getEmail());
        
        try {
            Resend client = getResendClient();
            if (client == null) {
                log.error("Resend client is null - cannot send email");
                return;
            }
            
            Context context = new Context();
            context.setVariable("name", user.getName());
            context.setVariable("email", user.getEmail());
            context.setVariable("loginUrl", BASE_URL + "/login");
            context.setVariable("shopUrl", BASE_URL + "/shop");
            
            String htmlContent = templateEngine.process("emails/welcome", context);
            
            String from = emailProperties.getFromName() + " <" + emailProperties.getFromEmail() + ">";
            
            CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(user.getEmail())
                .subject("Welcome to VedaThrifts!")
                .html(htmlContent)
                .build();

            CreateEmailResponse response = client.emails().send(options);
            log.info("Welcome email sent successfully! ID: {}", response.getId());
            
        } catch (ResendException e) {
            log.error("Resend API exception: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("UNEXPECTED ERROR in welcome email: {}", e.getMessage(), e);
            e.printStackTrace();
        }
        
        log.info("========== WELCOME EMAIL COMPLETE ==========");
    }

    public void sendOrderConfirmationEmail(Order order, User user) {
        log.info("========== ORDER CONFIRMATION EMAIL CALLED ==========");
        log.info("Order: {}, User: {}", 
            order != null ? order.getOrderNumber() : "null", 
            user != null ? user.getEmail() : "null");
        
        if (order == null || user == null) {
            log.error("Order or user is null!");
            return;
        }
        
        try {
            Resend client = getResendClient();
            if (client == null) {
                log.error("Resend client is null - cannot send email");
                return;
            }
            
            Context context = new Context();
            context.setVariable("order", order);
            context.setVariable("user", user);
            context.setVariable("orderUrl", BASE_URL + "/orders/" + order.getOrderNumber());
            context.setVariable("items", order.getItems());
            context.setVariable("total", order.getTotal());
            context.setVariable("shippingCost", order.getShippingCost());
            context.setVariable("subtotal", order.getSubtotal());
            
            String htmlContent = templateEngine.process("emails/order-confirmation", context);
            
            String from = emailProperties.getFromName() + " <" + emailProperties.getFromEmail() + ">";
            
            CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(user.getEmail())
                .subject("Order Confirmation #" + order.getOrderNumber())
                .html(htmlContent)
                .build();

            CreateEmailResponse response = client.emails().send(options);
            log.info("Order confirmation email sent! ID: {}", response.getId());
            
        } catch (ResendException e) {
            log.error("Resend API exception: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send order confirmation: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        log.info("========== PASSWORD RESET EMAIL CALLED ==========");
        log.info("User: {}, Token: {}", 
            user != null ? user.getEmail() : "null", 
            resetToken != null ? "present" : "null");
        
        if (user == null || resetToken == null) {
            log.error("User or token is null!");
            return;
        }
        
        try {
            Resend client = getResendClient();
            if (client == null) {
                log.error("Resend client is null - cannot send email");
                return;
            }
            
            String resetUrl = BASE_URL + "/reset-password?token=" + resetToken;
            
            Context context = new Context();
            context.setVariable("name", user.getName());
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("expiryHours", 24);
            
            String htmlContent = templateEngine.process("emails/password-reset", context);
            
            String from = emailProperties.getFromName() + " <" + emailProperties.getFromEmail() + ">";
            
            CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(user.getEmail())
                .subject("Reset Your VedaThrifts Password")
                .html(htmlContent)
                .build();

            CreateEmailResponse response = client.emails().send(options);
            log.info("Password reset email sent! ID: {}", response.getId());
            
        } catch (ResendException e) {
            log.error("Resend API exception: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send password reset: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }

    public void sendOrderStatusUpdateEmail(Order order, User user, String oldStatus, String newStatus) {
        log.info("========== ORDER STATUS UPDATE EMAIL CALLED ==========");
        log.info("Order: {}, User: {}, Status: {} -> {}", 
            order != null ? order.getOrderNumber() : "null",
            user != null ? user.getEmail() : "null",
            oldStatus, newStatus);
        
        if (order == null || user == null) {
            log.error("Order or user is null!");
            return;
        }
        
        try {
            Resend client = getResendClient();
            if (client == null) {
                log.error("Resend client is null - cannot send email");
                return;
            }
            
            Context context = new Context();
            context.setVariable("order", order);
            context.setVariable("user", user);
            context.setVariable("oldStatus", oldStatus);
            context.setVariable("newStatus", newStatus);
            context.setVariable("orderUrl", BASE_URL + "/orders/" + order.getOrderNumber());
            
            String htmlContent = templateEngine.process("emails/order-status-update", context);
            
            String from = emailProperties.getFromName() + " <" + emailProperties.getFromEmail() + ">";
            
            CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from)
                .to(user.getEmail())
                .subject("Order #" + order.getOrderNumber() + " Status Updated")
                .html(htmlContent)
                .build();

            CreateEmailResponse response = client.emails().send(options);
            log.info("Status update email sent! ID: {}", response.getId());
            
        } catch (ResendException e) {
            log.error("Resend API exception: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to send status update: {}", e.getMessage(), e);
            e.printStackTrace();
        }
    }
}