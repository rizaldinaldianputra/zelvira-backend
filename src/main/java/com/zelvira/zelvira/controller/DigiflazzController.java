package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.DigiflazzPriceListResponse;
import com.zelvira.zelvira.dto.DigiflazzResponse;
import com.zelvira.zelvira.dto.DigiflazzTransactionResponse;
import com.zelvira.zelvira.service.DigiflazzService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/digiflazz")
public class DigiflazzController {

    private final DigiflazzService digiflazzService;

    public DigiflazzController(DigiflazzService digiflazzService) {
        this.digiflazzService = digiflazzService;
    }

    @GetMapping("/cek-saldo")
    public ResponseEntity<DigiflazzResponse<DigiflazzTransactionResponse>> checkBalance() {
        return ResponseEntity.ok(digiflazzService.checkBalance());
    }

    @GetMapping("/price-list")
    public ResponseEntity<DigiflazzResponse<List<DigiflazzPriceListResponse>>> getPriceList(
            @RequestParam(required = false, defaultValue = "prepaid") String cmd) {
        return ResponseEntity.ok(digiflazzService.getPriceList(cmd));
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> digiflazzWebhook(
            @RequestBody DigiflazzResponse<DigiflazzTransactionResponse> payload) {
        // Digiflazz sends updates to this webhook when transaction status changes
        // Make sure to register this webhook URL in Digiflazz dashboard
        digiflazzService.processCallback(payload);
        return ResponseEntity.ok("OK");
    }
}
