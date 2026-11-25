package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.GoodsReceiptResponse;
import com.zelvira.zelvira.dto.PurchaseOrderRequest;
import com.zelvira.zelvira.dto.PurchaseOrderResponse;
import com.zelvira.zelvira.service.GoodsReceiptService;
import com.zelvira.zelvira.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final GoodsReceiptService goodsReceiptService;

    @PostMapping
    public ResponseEntity<PurchaseOrderResponse> createPurchaseOrder(@Valid @RequestBody PurchaseOrderRequest request) {
        return new ResponseEntity<>(purchaseOrderService.createPurchaseOrder(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<PurchaseOrderResponse>> getAllPurchaseOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(purchaseOrderService.getAllPurchaseOrders(
                page, size, search, supplierId, status, startDate, endDate, sortBy, sortDir));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getPurchaseOrderStats() {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderStats());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PurchaseOrderResponse> approvePurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.approvePurchaseOrder(id));
    }

    @GetMapping("/approved")
    public ResponseEntity<List<PurchaseOrderResponse>> getApprovedPurchaseOrders() {
        return ResponseEntity.ok(purchaseOrderService.getApprovedPurchaseOrders());
    }

    @GetMapping("/by-number/{poNumber}")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrderByPoNumber(@PathVariable String poNumber) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderByPoNumber(poNumber));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse> getPurchaseOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getPurchaseOrderById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseOrderResponse> updatePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.ok(purchaseOrderService.updatePurchaseOrder(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PurchaseOrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Field 'status' wajib diisi.");
        }
        return ResponseEntity.ok(purchaseOrderService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchaseOrder(@PathVariable Long id) {
        purchaseOrderService.deletePurchaseOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/receipts")
    public ResponseEntity<List<GoodsReceiptResponse>> getReceiptsByPurchaseOrder(@PathVariable Long id) {
        return ResponseEntity.ok(goodsReceiptService.getReceiptsByPurchaseOrder(id));
    }
}
