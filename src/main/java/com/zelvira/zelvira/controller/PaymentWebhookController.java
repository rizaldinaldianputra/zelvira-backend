package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({ "/api/v1/callbacks", "/api/callbacks" })
@Slf4j
public class PaymentWebhookController {

    private final OrderService orderService;
    private final com.zelvira.zelvira.service.TopupService topupService;
    private final com.zelvira.zelvira.service.DigitalOrderService digitalOrderService;

    public PaymentWebhookController(OrderService orderService,
            com.zelvira.zelvira.service.TopupService topupService,
            com.zelvira.zelvira.service.DigitalOrderService digitalOrderService) {
        this.orderService = orderService;
        this.topupService = topupService;
        this.digitalOrderService = digitalOrderService;
    }

    @PostMapping("/midtrans")
    public ResponseEntity<ApiResponse<String>> handleMidtransCallback(@RequestBody Map<String, Object> payload) {
        log.info("Received Midtrans Webhook: {}", payload);

        try {
            Object rawOrderId = payload.get("order_id");
            if (rawOrderId == null) {
                log.warn("Midtrans webhook received without order_id: {}", payload);
                return ResponseEntity.ok(ApiResponse.success("Missing order_id ignored", null));
            }
            String orderId = String.valueOf(rawOrderId);
            String transactionStatus = payload.get("transaction_status") != null
                    ? String.valueOf(payload.get("transaction_status"))
                    : "";
            String fraudStatus = payload.get("fraud_status") != null ? String.valueOf(payload.get("fraud_status")) : "";

            boolean isTopup = orderId.startsWith("TOPUP-");
            boolean isDigital = orderId.startsWith("DIGI-");

            if ("capture".equalsIgnoreCase(transactionStatus)) {
                if ("challenge".equalsIgnoreCase(fraudStatus)) {
                    if (isTopup)
                        topupService.processFailedTopup(orderId);
                    else if (isDigital)
                        digitalOrderService.handlePaymentFailed(orderId);
                    else
                        safeUpdateOrderStatus(orderId, "CHALLENGE");
                } else if ("accept".equalsIgnoreCase(fraudStatus) || fraudStatus.isEmpty()) {
                    if (isTopup)
                        topupService.processSuccessfulTopup(orderId);
                    else if (isDigital)
                        digitalOrderService.handlePaymentSuccess(orderId);
                    else
                        safeUpdateOrderStatus(orderId, "PAID");
                }
            } else if ("settlement".equalsIgnoreCase(transactionStatus)) {
                if (isTopup)
                    topupService.processSuccessfulTopup(orderId);
                else if (isDigital)
                    digitalOrderService.handlePaymentSuccess(orderId);
                else
                    safeUpdateOrderStatus(orderId, "PAID");
            } else if ("deny".equalsIgnoreCase(transactionStatus) || "expire".equalsIgnoreCase(transactionStatus)
                    || "cancel".equalsIgnoreCase(transactionStatus)) {
                if (isTopup)
                    topupService.processFailedTopup(orderId, transactionStatus);
                else if (isDigital)
                    digitalOrderService.handlePaymentFailed(orderId);
                else
                    safeUpdateOrderStatus(orderId, "CANCELLED");
            } else if ("pending".equalsIgnoreCase(transactionStatus)) {
                if (!isTopup && !isDigital)
                    safeUpdateOrderStatus(orderId, "PENDING");
            } else {
                log.info("Unhandled transaction status: {} for order: {}", transactionStatus, orderId);
            }

            return ResponseEntity.ok(ApiResponse.success("Webhook processed", null));
        } catch (Exception e) {
            log.error("Error processing Midtrans webhook", e);
            return ResponseEntity.ok(ApiResponse.error(500, e.getMessage()));
        }
    }

    private void safeUpdateOrderStatus(String orderNumber, String status) {
        try {
            orderService.updateOrderStatusByNumber(orderNumber, status);
        } catch (Exception e) {
            log.warn("Could not update order status for orderNumber {}: {}", orderNumber, e.getMessage());
        }
    }
}
