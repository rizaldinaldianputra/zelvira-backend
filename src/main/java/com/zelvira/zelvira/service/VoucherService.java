package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.entity.UserVoucher;
import com.zelvira.zelvira.entity.Voucher;
import com.zelvira.zelvira.repository.UserVoucherRepository;
import com.zelvira.zelvira.repository.VoucherRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    public VoucherService(VoucherRepository voucherRepository, UserVoucherRepository userVoucherRepository) {
        this.voucherRepository = voucherRepository;
        this.userVoucherRepository = userVoucherRepository;
    }

    public Page<Voucher> getAllVouchers(Pageable pageable) {
        return voucherRepository.findAll(pageable);
    }

    public List<Voucher> getAllVouchers() {
        return voucherRepository.findAll();
    }

    public Voucher getVoucherById(Long id) {
        return voucherRepository.findById(id).orElseThrow(() -> new RuntimeException("Voucher not found"));
    }

    public Voucher getVoucherByCode(String code) {
        return voucherRepository.findByCode(code).orElseThrow(() -> new RuntimeException("Voucher not found"));
    }

    public Voucher createVoucher(Voucher voucher) {
        if (voucher.getTargetMembership() == null || voucher.getTargetMembership().isEmpty()) {
            voucher.setTargetMembership("ALL");
        }
        return voucherRepository.save(voucher);
    }

    public Voucher updateVoucher(Long id, Voucher voucherDetails) {
        Voucher voucher = getVoucherById(id);
        voucher.setCode(voucherDetails.getCode());
        voucher.setDiscountAmount(voucherDetails.getDiscountAmount());
        voucher.setDiscountType(voucherDetails.getDiscountType() != null ? voucherDetails.getDiscountType() : "FIXED");
        voucher.setMaxDiscount(voucherDetails.getMaxDiscount());
        voucher.setMinPurchase(voucherDetails.getMinPurchase());
        voucher.setValidUntil(voucherDetails.getValidUntil());
        voucher.setIsActive(voucherDetails.getIsActive());
        if (voucherDetails.getTargetMembership() != null) {
            voucher.setTargetMembership(voucherDetails.getTargetMembership());
        }
        return voucherRepository.save(voucher);
    }

    public void deleteVoucher(Long id) {
        voucherRepository.deleteById(id);
    }

    public UserVoucher claimVoucher(User user, String code) {
        Voucher voucher = getVoucherByCode(code);
        if (!voucher.getIsActive()) {
            throw new RuntimeException("Voucher is not active");
        }
        if (voucher.getValidUntil() != null && voucher.getValidUntil().isBefore(java.time.LocalDate.now())) {
            throw new RuntimeException("Voucher has expired");
        }

        long points = user.getPoints() != null ? user.getPoints() : 0L;
        String target = voucher.getTargetMembership();
        if (target != null && !target.equals("ALL")) {
            if (target.equals("GOLD") && points < 100) {
                throw new RuntimeException("Voucher is only available for GOLD and above members");
            }
            if (target.equals("PLATINUM") && points < 300) {
                throw new RuntimeException("Voucher is only available for PLATINUM members");
            }
        }

        if (userVoucherRepository.existsByUserAndVoucher(user, voucher)) {
            throw new RuntimeException("Voucher already claimed");
        }

        UserVoucher userVoucher = UserVoucher.builder()
                .user(user)
                .voucher(voucher)
                .usageCount(0)
                .build();

        return userVoucherRepository.save(userVoucher);
    }

    public List<UserVoucher> getClaimedVouchers(User user) {
        return userVoucherRepository.findByUser(user);
    }

    public List<Voucher> getAvailableVouchers(User user) {
        List<Voucher> activeVouchers = voucherRepository.findByIsActiveTrue();
        List<UserVoucher> claimed = userVoucherRepository.findByUser(user);

        List<Long> claimedVoucherIds = claimed.stream()
                .map(uv -> uv.getVoucher().getId())
                .toList();

        long points = user.getPoints() != null ? user.getPoints() : 0L;

        return activeVouchers.stream()
                .filter(v -> !claimedVoucherIds.contains(v.getId()))
                .filter(v -> v.getValidUntil() == null || !v.getValidUntil().isBefore(java.time.LocalDate.now()))
                .filter(v -> {
                    String target = v.getTargetMembership();
                    if (target == null || target.equals("ALL")) {
                        return true;
                    }
                    if (target.equals("GOLD") && points >= 100) {
                        return true;
                    }
                    if (target.equals("PLATINUM") && points >= 300) {
                        return true;
                    }
                    if (target.equals("BRONZE")) {
                        return true;
                    }
                    return false;
                })
                .toList();
    }
}
