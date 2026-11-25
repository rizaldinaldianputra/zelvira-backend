package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.dto.OrderResponse;
import com.zelvira.zelvira.entity.Order;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Set<String> CUSTOMER_ALLOWED_STATUSES = Set.of("CANCELLED", "COMPLETED");

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    private boolean isStaffOrAdmin(CustomUserDetails userDetails) {
        if (userDetails == null) return false;
        boolean hasStaffAuthority = userDetails.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority();
                    return "ROLE_ADMIN".equals(auth)
                            || "ROLE_CUSTOMER_SERVICE".equals(auth)
                            || "ROLE_SHIPPING".equals(auth);
                });
        if (hasStaffAuthority) return true;
        return userDetails.getUser() != null && userDetails.getUser().getSystemRole() != null;
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (isStaffOrAdmin(userDetails)) {
            return ResponseEntity.ok(orderService.mapToResponseList(orderService.getAllOrders()));
        }
        return ResponseEntity.ok(orderService.mapToResponseList(orderService.getOrdersByUserId(userDetails.getId())));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<Order> orders = orderService.getOrdersByUserId(userDetails.getId());
        boolean hasPending = false;
        for (Order o : orders) {
            if ("PENDING".equalsIgnoreCase(o.getStatus())) {
                hasPending = true;
                try {
                    orderService.syncPaymentStatus(o.getId());
                } catch (Exception e) {
                    // Ignore sync error, keep order as is
                }
            }
        }
        if (hasPending) {
            orders = orderService.getOrdersByUserId(userDetails.getId());
        }
        return ResponseEntity.ok(ApiResponse.success("Orders fetched successfully",
                orderService.mapToResponseList(orders)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Order order = orderService.getOrderById(id);
        if (!isStaffOrAdmin(userDetails) && (order.getUserId() == null || !order.getUserId().equals(userDetails.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak untuk pesanan ini");
        }
        if ("PENDING".equalsIgnoreCase(order.getStatus())) {
            try {
                order = orderService.syncPaymentStatus(id);
            } catch (Exception e) {
                // Ignore sync error
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Order fetched successfully",
                orderService.mapToResponse(order)));
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByNumber(
            @PathVariable String orderNumber,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Order order = orderService.getOrderByOrderNumber(orderNumber);
        if (!isStaffOrAdmin(userDetails) && (order.getUserId() == null || !order.getUserId().equals(userDetails.getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak untuk pesanan ini");
        }
        if ("PENDING".equalsIgnoreCase(order.getStatus())) {
            try {
                order = orderService.syncPaymentStatus(order.getId());
            } catch (Exception e) {
                // Ignore sync error
            }
        }
        return ResponseEntity.ok(ApiResponse.success("Order fetched successfully",
                orderService.mapToResponse(order)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestBody Order order,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null && !isStaffOrAdmin(userDetails)) {
            order.setUserId(userDetails.getId());
        }
        return ResponseEntity.ok(ApiResponse.success("Order created successfully",
                orderService.mapToResponse(orderService.createOrder(order))));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String status = payload.get("status");
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status wajib diisi");
        }

        Order order = orderService.getOrderById(id);
        if (!isStaffOrAdmin(userDetails)) {
            if (order.getUserId() == null || !order.getUserId().equals(userDetails.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak untuk pesanan ini");
            }
            if (!CUSTOMER_ALLOWED_STATUSES.contains(status.toUpperCase())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Pelanggan hanya dapat membatalkan atau menyelesaikan pesanan");
            }
        }

        return ResponseEntity.ok(orderService.mapToResponse(orderService.updateOrderStatus(id, status)));
    }

    @PostMapping("/{id}/payment-token")
    public ResponseEntity<ApiResponse<Map<String, String>>> getPaymentToken(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        try {
            String token = orderService.getOrGeneratePaymentToken(
                    id,
                    userDetails != null ? userDetails.getId() : null,
                    isStaffOrAdmin(userDetails));
            Order order = orderService.getOrderById(id);
            return ResponseEntity.ok(ApiResponse.success("Token pembayaran berhasil diperoleh",
                    Map.of(
                            "paymentToken", token != null ? token : "",
                            "paymentUrl", order.getPaymentUrl() != null ? order.getPaymentUrl() : ""
                    )));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id}/sync-payment")
    public ResponseEntity<ApiResponse<OrderResponse>> syncPayment(@PathVariable Long id) {
        try {
            Order synced = orderService.syncPaymentStatus(id);
            return ResponseEntity.ok(ApiResponse.success("Payment status synchronized", orderService.mapToResponse(synced)));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    @PostMapping("/number/{orderNumber}/sync-payment")
    public ResponseEntity<ApiResponse<OrderResponse>> syncPaymentByNumber(@PathVariable String orderNumber) {
        try {
            Order synced = orderService.syncPaymentStatusByOrderNumber(orderNumber);
            return ResponseEntity.ok(ApiResponse.success("Payment status synchronized", orderService.mapToResponse(synced)));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (!isStaffOrAdmin(userDetails)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya admin yang dapat menghapus pesanan");
        }
        orderService.deleteOrder(id);
        return ResponseEntity.ok().build();
    }
}
