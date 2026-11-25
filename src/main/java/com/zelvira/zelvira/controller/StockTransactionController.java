package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.StockSummaryResponse;
import com.zelvira.zelvira.dto.StockTransactionRequest;
import com.zelvira.zelvira.dto.StockTransactionResponse;
import com.zelvira.zelvira.service.StockTransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock-transactions")
@CrossOrigin(origins = "*")
public class StockTransactionController {

    private final StockTransactionService transactionService;

    public StockTransactionController(StockTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/add")
    public ResponseEntity<StockTransactionResponse> addStock(@RequestBody StockTransactionRequest request) {
        return ResponseEntity.ok(transactionService.addStock(request));
    }

    @PostMapping("/adjust")
    public ResponseEntity<StockTransactionResponse> adjustStock(@RequestBody StockTransactionRequest request) {
        return ResponseEntity.ok(transactionService.adjustStock(request));
    }

    @GetMapping("/variant/{variantId}")
    public ResponseEntity<Page<StockTransactionResponse>> getHistoryByVariant(
            @PathVariable Long variantId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(transactionService.getHistoryByVariant(variantId, pageable));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<StockTransactionResponse>> getHistoryByProduct(
            @PathVariable Long productId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(transactionService.getHistoryByProduct(productId, pageable));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<StockTransactionResponse>> getHistory(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(transactionService.searchHistory(query, pageable));
    }

    @GetMapping("/summary/{variantId}")
    public ResponseEntity<StockSummaryResponse> getSummary(@PathVariable Long variantId) {
        return ResponseEntity.ok(transactionService.getStockSummary(variantId));
    }

    @GetMapping("/summaries")
    public ResponseEntity<java.util.List<StockSummaryResponse>> getSummaries(
            @RequestParam java.util.List<Long> variantIds) {
        return ResponseEntity.ok(transactionService.getSummariesForVariants(variantIds));
    }
}
