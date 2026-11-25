package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.AddressRequest;
import com.zelvira.zelvira.dto.AddressResponse;
import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.AddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getUserAddresses(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Addresses fetched successfully",
                addressService.getUserAddresses(userDetails.getUsername())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Address created successfully",
                addressService.createAddress(userDetails.getUsername(), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody AddressRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully",
                addressService.updateAddress(userDetails.getUsername(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        addressService.deleteAddress(userDetails.getUsername(), id);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully", null));
    }

    @PatchMapping("/{id}/set-default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Default address set successfully",
                addressService.setDefaultAddress(userDetails.getUsername(), id)));
    }
}
