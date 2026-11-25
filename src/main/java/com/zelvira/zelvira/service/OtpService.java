package com.zelvira.zelvira.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@Slf4j
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final WhatsAppService whatsappService;

    public OtpService(StringRedisTemplate redisTemplate, WhatsAppService whatsappService) {
        this.redisTemplate = redisTemplate;
        this.whatsappService = whatsappService;
    }

    private static final String OTP_PREFIX = "OTP:";
    private static final int OTP_EXPIRY_MINUTES = 5;

    public static String normalizePhone(String phoneNumber) {
        if (phoneNumber == null)
            return null;
        String normalizedPhone = phoneNumber.replaceAll("[^0-9]", "");
        if (normalizedPhone.startsWith("0")) {
            normalizedPhone = "62" + normalizedPhone.substring(1);
        } else if (!normalizedPhone.startsWith("62")) {
            normalizedPhone = "62" + normalizedPhone; // Fallback to 62 if no country code
        }
        return normalizedPhone;
    }

    public String generateOtp(String phoneNumber) {
        String code = String.format("%06d", new Random().nextInt(1000000));

        // Normalize phone number (convert 08... to 628...)
        String normalizedPhone = normalizePhone(phoneNumber);

        // Store in Redis with TTL using normalizedPhone AND raw phoneNumber
        redisTemplate.opsForValue().set(OTP_PREFIX + normalizedPhone, code, Duration.ofMinutes(OTP_EXPIRY_MINUTES));
        if (phoneNumber != null && !phoneNumber.equals(normalizedPhone)) {
            redisTemplate.opsForValue().set(OTP_PREFIX + phoneNumber, code, Duration.ofMinutes(OTP_EXPIRY_MINUTES));
        }

        // Send via Zenziva Official WhatsApp OTP
        boolean delivered = whatsappService.sendOtp(normalizedPhone, code);

        log.info("**************************************************");
        log.info("OTP GENERATED FOR {} (normalized: {}): {}", phoneNumber, normalizedPhone, code);
        if (delivered) {
            log.info("WhatsApp OTP delivered via Zenziva to {}", normalizedPhone);
        } else {
            log.warn("WhatsApp OTP NOT delivered to {} — check Zenziva API credentials or balance", normalizedPhone);
        }
        log.info("**************************************************");

        return code;
    }

    public boolean verifyOtp(String phoneNumber, String code) {
        String normalizedPhone = normalizePhone(phoneNumber);
        String storedCode = redisTemplate.opsForValue().get(OTP_PREFIX + normalizedPhone);
        if (storedCode == null && phoneNumber != null) {
            storedCode = redisTemplate.opsForValue().get(OTP_PREFIX + phoneNumber);
        }

        if (storedCode != null && storedCode.equals(code)) {
            // Delete code after successful verification (one-time use)
            redisTemplate.delete(OTP_PREFIX + normalizedPhone);
            if (phoneNumber != null) {
                redisTemplate.delete(OTP_PREFIX + phoneNumber);
            }
            return true;
        }

        return false;
    }

    private static final String EMAIL_OTP_PREFIX = "EMAIL_OTP:";
    private static final String EMAIL_OTP_VERIFIED_PREFIX = "EMAIL_OTP_VERIFIED:";

    public String generateEmailOtp(String email) {
        String cleanEmail = email.trim().toLowerCase();
        String code = String.format("%06d", new Random().nextInt(1000000));
        redisTemplate.opsForValue().set(EMAIL_OTP_PREFIX + cleanEmail, code, Duration.ofMinutes(OTP_EXPIRY_MINUTES));
        log.info("Email OTP generated for {}: {}", cleanEmail, code);
        return code;
    }

    public boolean verifyEmailOtp(String email, String code) {
        if (email == null || code == null)
            return false;
        String cleanEmail = email.trim().toLowerCase();
        String storedCode = redisTemplate.opsForValue().get(EMAIL_OTP_PREFIX + cleanEmail);
        if (storedCode != null && storedCode.equals(code.trim())) {
            redisTemplate.delete(EMAIL_OTP_PREFIX + cleanEmail);
            redisTemplate.opsForValue().set(EMAIL_OTP_VERIFIED_PREFIX + cleanEmail, "VERIFIED", Duration.ofMinutes(15));
            return true;
        }
        return false;
    }

    public boolean isEmailOtpVerified(String email) {
        if (email == null)
            return false;
        String cleanEmail = email.trim().toLowerCase();
        String status = redisTemplate.opsForValue().get(EMAIL_OTP_VERIFIED_PREFIX + cleanEmail);
        return "VERIFIED".equalsIgnoreCase(status);
    }

    public void clearEmailOtpVerification(String email) {
        if (email == null)
            return;
        String cleanEmail = email.trim().toLowerCase();
        redisTemplate.delete(EMAIL_OTP_VERIFIED_PREFIX + cleanEmail);
        redisTemplate.delete(EMAIL_OTP_PREFIX + cleanEmail);
    }
}
