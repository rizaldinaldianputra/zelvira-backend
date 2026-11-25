package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.UserVoucher;
import com.zelvira.zelvira.entity.Voucher;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.VoucherService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vouchers")
@CrossOrigin(origins = "*")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @GetMapping
    public ResponseEntity<Page<Voucher>> getAllVouchers(Pageable pageable) {
        return ResponseEntity.ok(voucherService.getAllVouchers(pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Voucher>> getAllVouchersList() {
        return ResponseEntity.ok(voucherService.getAllVouchers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Voucher> getVoucherById(@PathVariable Long id) {
        return ResponseEntity.ok(voucherService.getVoucherById(id));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<Voucher> getVoucherByCode(@PathVariable String code) {
        return ResponseEntity.ok(voucherService.getVoucherByCode(code));
    }

    @PostMapping
    public ResponseEntity<Voucher> createVoucher(@RequestBody Voucher voucher) {
        return ResponseEntity.ok(voucherService.createVoucher(voucher));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Voucher> updateVoucher(@PathVariable Long id, @RequestBody Voucher voucher) {
        return ResponseEntity.ok(voucherService.updateVoucher(id, voucher));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVoucher(@PathVariable Long id) {
        voucherService.deleteVoucher(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/claim/{code}")
    public ResponseEntity<UserVoucher> claimVoucher(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String code) {
        return ResponseEntity.ok(voucherService.claimVoucher(userDetails.getUser(), code));
    }

    @GetMapping("/my-vouchers")
    public ResponseEntity<List<UserVoucher>> getClaimedVouchers(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(voucherService.getClaimedVouchers(userDetails.getUser()));
    }

    @GetMapping("/available")
    public ResponseEntity<List<Voucher>> getAvailableVouchers(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(voucherService.getAvailableVouchers(userDetails.getUser()));
    }
}
