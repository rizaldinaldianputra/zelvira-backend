package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.dto.CheckoutRequest;
import com.zelvira.zelvira.dto.OrderResponse;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
@CrossOrigin(origins = "*")
public class CheckoutController {

    private final OrderService orderService;

    public CheckoutController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CheckoutRequest request) {
        OrderResponse order = orderService.checkout(userDetails.getUser(), request);
        return ResponseEntity.ok(ApiResponse.success("Checkout successful", order));
    }
}
