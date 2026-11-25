package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.GoodsReceiptRequest;
import com.zelvira.zelvira.dto.GoodsReceiptResponse;
import com.zelvira.zelvira.service.GoodsReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/goods-receipts")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    @PostMapping
    public ResponseEntity<GoodsReceiptResponse> createGoodsReceipt(@Valid @RequestBody GoodsReceiptRequest request) {
        return new ResponseEntity<>(goodsReceiptService.createGoodsReceipt(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<GoodsReceiptResponse>> getAllGoodsReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long poId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return ResponseEntity.ok(goodsReceiptService.getAllGoodsReceipts(
                page, size, search, supplierId, poId, startDate, endDate, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GoodsReceiptResponse> getGoodsReceiptById(@PathVariable Long id) {
        return ResponseEntity.ok(goodsReceiptService.getGoodsReceiptById(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<GoodsReceiptResponse> approveGoodsReceipt(@PathVariable Long id) {
        return ResponseEntity.ok(goodsReceiptService.approveGoodsReceipt(id));
    }
}
