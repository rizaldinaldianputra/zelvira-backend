package com.zelvira.zelvira.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WhatsApp integration powered by Zenziva Official WhatsApp API (Meta WABA).
 * Replaced wajs / Puppeteer microservice with Zenziva cloud gateway.
 */
@Service
@Slf4j
public class WhatsAppService {

    private final ZenzivaService zenzivaService;
    private static final Pattern OTP_PATTERN = Pattern.compile("\\b(\\d{4,8})\\b");

    public WhatsAppService(ZenzivaService zenzivaService) {
        this.zenzivaService = zenzivaService;
    }

    /**
     * Sends an OTP to the given phone number via Zenziva Official WhatsApp API.
     *
     * @param phoneNumber Destination phone number
     * @param otpCode 4 to 8 digit numeric OTP
     * @return true if accepted by Zenziva API
     */
    public boolean sendOtp(String phoneNumber, String otpCode) {
        return zenzivaService.sendOtp(phoneNumber, otpCode);
    }

    /**
     * Compatibility method for generic message sends (e.g. ChatController, legacy callers).
     * If the message contains an OTP code, it extracts and sends it through Zenziva OTP API.
     */
    public boolean sendText(String phoneNumber, String message) {
        if (phoneNumber == null || message == null) {
            return false;
        }

        // Try extracting OTP digits from the message
        Matcher matcher = OTP_PATTERN.matcher(message);
        if (matcher.find()) {
            String extractedOtp = matcher.group(1);
            log.info("Extracted OTP [{}] from message for {}; routing to Zenziva Official OTP API", extractedOtp, phoneNumber);
            return zenzivaService.sendOtp(phoneNumber, extractedOtp);
        }

        log.warn("sendText called for {} with non-OTP text. Zenziva Official API is dedicated for OTP messages.", phoneNumber);
        return false;
    }

    /**
     * Connection and status info for Admin WhatsApp Dashboard.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getStatus() {
        Map<String, Object> rawStatus = zenzivaService.getStatus();
        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : rawStatus.entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }

    /**
     * Legacy session logout (Zenziva HTTP API is stateless, no session disconnect required).
     */
    public void logoutSession() {
        log.info("WhatsApp session logout requested. Zenziva HTTP API is stateless, no session to drop.");
    }

    /**
     * Legacy QR code endpoint: Zenziva Official WABA does not require pairing via QR.
     * Returns null.
     */
    public byte[] getQrCode() {
        log.info("QR code requested. Zenziva WhatsApp Official API is active; QR scanning is not required.");
        return null;
    }

    public ZenzivaService getZenzivaService() {
        return zenzivaService;
    }
}
