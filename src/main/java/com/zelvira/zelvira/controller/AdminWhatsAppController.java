package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.service.WhatsAppService;
import com.zelvira.zelvira.service.ZenzivaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/v1/admin/whatsapp")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminWhatsAppController {

    private final WhatsAppService whatsappService;
    private final ZenzivaService zenzivaService;

    public AdminWhatsAppController(WhatsAppService whatsappService, ZenzivaService zenzivaService) {
        this.whatsappService = whatsappService;
        this.zenzivaService = zenzivaService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = zenzivaService.getStatus();
        return ResponseEntity.ok(ApiResponse.success("Status fetched successfully", status));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendTestOtp(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "Nomor WhatsApp tujuan wajib diisi."));
        }

        String otp = request.get("otp");
        if (otp == null || otp.isBlank()) {
            otp = String.format("%06d", new Random().nextInt(1000000));
        }

        String brand = request.get("brand");
        Map<String, Object> result = zenzivaService.sendOtpWithDetails(phoneNumber, otp, brand);
        result.put("sentOtp", otp);

        boolean success = Boolean.TRUE.equals(result.get("success"));
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("Kode OTP WhatsApp berhasil dikirim via Zenziva.", result));
        } else {
            return ResponseEntity.ok(ApiResponse.error(400, "Gagal mengirim OTP: " + result.get("text")));
        }
    }

    @PostMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateConfig(@RequestBody Map<String, String> request) {
        String apiUrl = request.get("apiUrl");
        String userkey = request.get("userkey");
        String passkey = request.get("passkey");
        String brand = request.get("brand");

        zenzivaService.updateConfig(apiUrl, userkey, passkey, brand);
        return ResponseEntity.ok(ApiResponse.success("Konfigurasi Zenziva berhasil diperbarui.", zenzivaService.getStatus()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logoutSession() {
        whatsappService.logoutSession();
        return ResponseEntity.ok(ApiResponse.success("Zenziva API stateless; tidak ada sesi lokal yang perlu ditutup.", null));
    }

    @GetMapping(value = "/qr")
    public ResponseEntity<ApiResponse<Map<String, String>>> getQrCode() {
        return ResponseEntity.ok(ApiResponse.success(
                "Zenziva Official WhatsApp API aktif. Tidak diperlukan scan QR Code.",
                Map.of("status", "READY", "provider", "ZENZIVA", "message", "Official WhatsApp API is active.")
        ));
    }
}
