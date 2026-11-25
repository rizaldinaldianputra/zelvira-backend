package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.DigiflazzResponse;
import com.zelvira.zelvira.dto.DigiflazzTransactionResponse;
import com.zelvira.zelvira.dto.DigitalOrderRequest;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.DigitalOrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import com.zelvira.zelvira.entity.DigitalOrder;

@RestController
@RequestMapping("/api/orders")
public class DigitalOrderController {

    private final DigitalOrderService digitalOrderService;

    public DigitalOrderController(DigitalOrderService digitalOrderService) {
        this.digitalOrderService = digitalOrderService;
    }

    @PostMapping("/prepaid")
    public ResponseEntity<?> createPrepaidOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody DigitalOrderRequest request) {
        try {
            Long userId = userDetails.getUser().getId();
            return ResponseEntity.ok(digitalOrderService.processPrepaidOrder(userId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/postpaid/inquiry")
    public ResponseEntity<?> inquiryPostpaid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody DigitalOrderRequest request) {
        try {
            Long userId = userDetails.getUser().getId();
            return ResponseEntity.ok(digitalOrderService.processPostpaidInquiry(userId, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/digital/create")
    public ResponseEntity<?> createDigitalOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody DigitalOrderRequest request) {
        try {
            Long userId = userDetails != null && userDetails.getUser() != null ? userDetails.getUser().getId() : null;
            String userEmail = userDetails != null && userDetails.getUser() != null ? userDetails.getUser().getEmail()
                    : null;
            return ResponseEntity.ok(digitalOrderService.createDigitalOrder(userId, userEmail, request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/digital/{orderNumber}")
    public ResponseEntity<?> getDigitalOrder(@PathVariable String orderNumber) {
        try {
            return ResponseEntity.ok(digitalOrderService.getOrderByNumber(orderNumber));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/digital/my")
    public ResponseEntity<?> getMyDigitalOrders(@AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            Long userId = userDetails.getUser().getId();
            return ResponseEntity.ok(digitalOrderService.getMyOrders(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/postpaid/pay")
    public ResponseEntity<?> payPostpaid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, Object> payload) {
        try {
            Long userId = userDetails.getUser().getId();
            DigitalOrderRequest request = new DigitalOrderRequest();
            request.setSkuCode((String) payload.get("skuCode"));
            request.setCustomerNo((String) payload.get("customerNo"));

            Double amountToDeduct = Double.valueOf(payload.get("amount").toString());
            String refId = (String) payload.get("refId");

            return ResponseEntity.ok(digitalOrderService.processPostpaidPay(userId, request, amountToDeduct, refId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
