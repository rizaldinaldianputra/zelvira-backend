package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.*;
import com.zelvira.zelvira.service.PricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Product Pricing Management", description = "Manajemen HPP, Master Biaya, Formula Dinamis, Kalkulasi Harga Jual, dan Analisis Keuntungan")
public class PricingController {

    private final PricingService pricingService;

    // ==========================================
    // 1. MASTER BIAYA
    // ==========================================

    @GetMapping("/costs")
    @Operation(summary = "Ambil semua komponen master biaya", description = "Mengembalikan daftar biaya kemasan, operasional, marketing, dan lainnya")
    public ResponseEntity<ApiResponse<List<PricingCostItemResponse>>> getAllCostItems() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.getAllCostItems()));
    }

    @PostMapping("/costs")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Tambah variabel biaya baru")
    public ResponseEntity<ApiResponse<PricingCostItemResponse>> createCostItem(@Valid @RequestBody PricingCostItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Komponen biaya berhasil ditambahkan", pricingService.createCostItem(req)));
    }

    @PutMapping("/costs/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Perbarui variabel biaya")
    public ResponseEntity<ApiResponse<PricingCostItemResponse>> updateCostItem(
            @PathVariable Long id,
            @Valid @RequestBody PricingCostItemRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Komponen biaya berhasil diperbarui", pricingService.updateCostItem(id, req)));
    }

    @DeleteMapping("/costs/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Hapus variabel biaya")
    public ResponseEntity<ApiResponse<Void>> deleteCostItem(@PathVariable Long id) {
        pricingService.deleteCostItem(id);
        return ResponseEntity.ok(ApiResponse.success("Komponen biaya berhasil dihapus", null));
    }

    // ==========================================
    // 2. MASTER FORMULA & PENGATURAN HARGA
    // ==========================================

    @GetMapping("/formulas")
    @Operation(summary = "Ambil semua formula harga dinamis")
    public ResponseEntity<ApiResponse<List<PricingFormulaResponse>>> getAllFormulas() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.getAllFormulas()));
    }

    @PostMapping("/formulas")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Tambah formula harga dinamis baru")
    public ResponseEntity<ApiResponse<PricingFormulaResponse>> createFormula(@Valid @RequestBody PricingFormulaRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Formula harga berhasil disimpan", pricingService.createFormula(req)));
    }

    @PutMapping("/formulas/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Perbarui formula harga dinamis")
    public ResponseEntity<ApiResponse<PricingFormulaResponse>> updateFormula(
            @PathVariable Long id,
            @Valid @RequestBody PricingFormulaRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Formula harga berhasil diperbarui", pricingService.updateFormula(id, req)));
    }

    @DeleteMapping("/formulas/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Hapus formula harga dinamis")
    public ResponseEntity<ApiResponse<Void>> deleteFormula(@PathVariable Long id) {
        pricingService.deleteFormula(id);
        return ResponseEntity.ok(ApiResponse.success("Formula harga berhasil dihapus", null));
    }

    // ==========================================
    // 3. KALKULASI HARGA & REALTIME SIMULATION
    // ==========================================

    @PostMapping("/calculate")
    @Operation(summary = "Simulasi kalkulasi harga secara realtime")
    public ResponseEntity<ApiResponse<PriceCalculationResponse>> calculatePrice(@RequestBody PriceCalculationRequest req) {
        return ResponseEntity.ok(ApiResponse.success(pricingService.calculatePrice(req)));
    }

    // ==========================================
    // 4. INTEGRASI MASTER PRODUK EXISTING
    // ==========================================

    @GetMapping("/products")
    @Operation(summary = "Daftar produk existing lengkap dengan kalkulasi HPP dan harga jual")
    public ResponseEntity<ApiResponse<List<ProductPricingResponse>>> getProductPricingList(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(pricingService.getProductPricingList(search, categoryId)));
    }

    @PostMapping("/apply")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Terapkan harga hasil kalkulasi ke ProductVariant.price toko")
    public ResponseEntity<ApiResponse<ProductPricingResponse>> applyPrice(@RequestBody ApplyPriceRequest req) {
        return ResponseEntity.ok(ApiResponse.success("Harga produk berhasil diterapkan ke katalog toko", pricingService.applyPrice(req)));
    }

    @PostMapping("/batch-apply")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Terapkan harga untuk banyak produk sekaligus")
    public ResponseEntity<ApiResponse<Integer>> batchApplyPrices(@RequestBody List<ApplyPriceRequest> requests) {
        int count = pricingService.batchApplyPrices(requests);
        return ResponseEntity.ok(ApiResponse.success("Berhasil menerapkan harga untuk " + count + " item", count));
    }

    // ==========================================
    // 5. ANALISIS KEUNTUNGAN
    // ==========================================

    @GetMapping("/analytics")
    @Operation(summary = "Ambil statistik analisis laba, HPP, omzet, dan performa margin produk")
    public ResponseEntity<ApiResponse<PricingAnalyticsResponse>> getPricingAnalytics() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.getPricingAnalytics()));
    }
}

