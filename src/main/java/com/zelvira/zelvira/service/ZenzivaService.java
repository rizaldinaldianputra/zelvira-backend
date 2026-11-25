package com.zelvira.zelvira.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to handle WhatsApp Official OTP messages through Zenziva API.
 * Endpoint: POST https://console.zenziva.net/waofficial/api/sendWAOfficial/
 * Parameters: userkey, passkey, to, brand, otp
 */
@Service
@Slf4j
public class ZenzivaService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.zenziva.url:https://console.zenziva.net/waofficial/api/sendWAOfficial/}")
    private String zenzivaUrl;

    @Value("${app.zenziva.userkey:}")
    private String userkey;

    @Value("${app.zenziva.passkey:}")
    private String passkey;

    @Value("${app.zenziva.brand:Zelvira}")
    private String brand;

    public ZenzivaService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    public ZenzivaService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Clean and format phone number for Zenziva API.
     * Supports formats like: 0812..., +62812..., 62812...
     */
    public String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return "";
        }
        String clean = phoneNumber.replaceAll("[^0-9]", "");
        if (clean.startsWith("0")) {
            clean = "62" + clean.substring(1);
        } else if (!clean.startsWith("62")) {
            clean = "62" + clean;
        }
        return clean;
    }

    /**
     * Sends an OTP code to the recipient using Zenziva WhatsApp Official API.
     *
     * @param to Phone number
     * @param otp Numeric OTP code (max 10 digits)
     * @return true if Zenziva returned status "1" (Success), false otherwise
     */
    public boolean sendOtp(String to, String otp) {
        return sendOtp(to, otp, this.brand);
    }

    /**
     * Sends an OTP code with custom brand name.
     */
    public boolean sendOtp(String to, String otp, String customBrand) {
        Map<String, Object> result = sendOtpWithDetails(to, otp, customBrand);
        return Boolean.TRUE.equals(result.get("success"));
    }

    /**
     * Sends an OTP and returns detailed response map from Zenziva API.
     */
    public Map<String, Object> sendOtpWithDetails(String to, String otp, String customBrand) {
        Map<String, Object> result = new HashMap<>();
        String targetPhone = normalizePhone(to);
        String selectedBrand = (customBrand != null && !customBrand.isBlank()) ? customBrand : this.brand;
        if (selectedBrand.length() > 25) {
            selectedBrand = selectedBrand.substring(0, 25);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("userkey", this.userkey);
            body.add("passkey", this.passkey);
            body.add("to", targetPhone);
            body.add("brand", selectedBrand);
            body.add("otp", otp != null ? otp.trim() : "");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            log.info("Sending Zenziva Official WhatsApp OTP to {} (Brand: {})", targetPhone, selectedBrand);

            ResponseEntity<String> response = restTemplate.postForEntity(this.zenzivaUrl, request, String.class);
            String responseBody = response.getBody();

            log.info("Zenziva API Response for {}: {}", targetPhone, responseBody);

            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode json = objectMapper.readTree(responseBody);
                String status = json.has("status") ? json.get("status").asText() : "0";
                String text = json.has("text") ? json.get("text").asText() : "";
                
                boolean isSuccess = "1".equals(status);
                result.put("success", isSuccess);
                result.put("status", status);
                result.put("text", text);
                result.put("to", json.has("to") ? json.get("to").asText() : targetPhone);
                
                if (json.has("messageId")) {
                    result.put("messageId", json.get("messageId").asText());
                }
                if (json.has("cost")) {
                    result.put("cost", json.get("cost").asInt());
                }

                if (isSuccess) {
                    log.info("Successfully delivered WhatsApp OTP via Zenziva to {}", targetPhone);
                } else {
                    log.warn("Zenziva API returned non-success for {}: status={}, text={}", targetPhone, status, text);
                }
                return result;
            }

            result.put("success", false);
            result.put("text", "Empty response from Zenziva API");
            return result;
        } catch (Exception e) {
            log.error("Exception calling Zenziva WhatsApp API for {}: {}", targetPhone, e.getMessage(), e);
            result.put("success", false);
            result.put("status", "0");
            result.put("text", e.getMessage());
            return result;
        }
    }

    /**
     * Returns configuration and connectivity status for Admin dashboard.
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean isConfigured = userkey != null && !userkey.isBlank() && passkey != null && !passkey.isBlank();

        status.put("provider", "ZENZIVA");
        status.put("service", "WhatsApp Official OTP (Meta WABA)");
        status.put("status", isConfigured ? "READY" : "NOT_CONFIGURED");
        status.put("brand", brand);
        status.put("apiUrl", zenzivaUrl);
        status.put("userkey", maskKey(userkey));
        status.put("rawUserkey", userkey);
        status.put("passkey", maskKey(passkey));
        status.put("deviceNumber", "Official WhatsApp Business API (" + brand + ")");
        status.put("configured", isConfigured);
        status.put("wabaVerified", true);
        return status;
    }

    public void updateConfig(String newUrl, String newUserkey, String newPasskey, String newBrand) {
        if (newUrl != null && !newUrl.isBlank()) {
            this.zenzivaUrl = newUrl.trim();
        }
        if (newUserkey != null && !newUserkey.isBlank()) {
            this.userkey = newUserkey.trim();
        }
        if (newPasskey != null && !newPasskey.isBlank()) {
            this.passkey = newPasskey.trim();
        }
        if (newBrand != null && !newBrand.isBlank()) {
            this.brand = newBrand.trim();
        }
        log.info("Updated Zenziva WhatsApp configuration: brand={}, userkey={}", this.brand, maskKey(this.userkey));
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 6) return "******";
        return key.substring(0, 4) + "••••••••" + key.substring(key.length() - 4);
    }

    public String getUserkey() {
        return userkey;
    }

    public String getBrand() {
        return brand;
    }

    public String getZenzivaUrl() {
        return zenzivaUrl;
    }
}
