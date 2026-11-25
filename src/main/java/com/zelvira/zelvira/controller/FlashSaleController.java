package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.FlashSaleRequest;
import com.zelvira.zelvira.dto.FlashSaleResponse;
import com.zelvira.zelvira.service.FlashSaleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flash-sales")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    public FlashSaleController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    @PostMapping
    public ResponseEntity<FlashSaleResponse> createFlashSale(@RequestBody FlashSaleRequest request) {
        return ResponseEntity.ok(flashSaleService.createFlashSale(request));
    }

    @GetMapping
    public ResponseEntity<List<FlashSaleResponse>> getAllFlashSales(
            @RequestParam(required = false) String platform) {
        if (platform != null && !platform.isEmpty()) {
            return ResponseEntity.ok(flashSaleService.getAllFlashSalesByPlatform(platform));
        }
        return ResponseEntity.ok(flashSaleService.getAllFlashSales());
    }

    @GetMapping("/active")
    public ResponseEntity<List<FlashSaleResponse>> getActiveFlashSales(
            @RequestParam(required = false) String platform) {
        if (platform != null && !platform.isEmpty()) {
            return ResponseEntity.ok(flashSaleService.getActiveFlashSalesByPlatform(platform));
        }
        return ResponseEntity.ok(flashSaleService.getActiveFlashSales());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlashSaleResponse> getFlashSaleById(@PathVariable Long id) {
        return ResponseEntity.ok(flashSaleService.getFlashSaleById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlashSaleResponse> updateFlashSale(@PathVariable Long id,
            @RequestBody FlashSaleRequest request) {
        return ResponseEntity.ok(flashSaleService.updateFlashSale(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlashSale(@PathVariable Long id) {
        flashSaleService.deleteFlashSale(id);
        return ResponseEntity.noContent().build();
    }
}
