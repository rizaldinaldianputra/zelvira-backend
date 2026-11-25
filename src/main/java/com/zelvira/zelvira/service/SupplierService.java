package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.SupplierRequest;
import com.zelvira.zelvira.dto.SupplierResponse;
import com.zelvira.zelvira.entity.Supplier;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierService {

    private final SupplierRepository supplierRepository;

    @Transactional
    public SupplierResponse createSupplier(SupplierRequest request) {
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            code = generateSupplierCode();
        } else {
            code = code.trim().toUpperCase();
            if (supplierRepository.existsByCode(code)) {
                throw new IllegalArgumentException("Kode supplier '" + code + "' sudah digunakan.");
            }
        }

        Supplier supplier = Supplier.builder()
                .code(code)
                .name(request.getName().trim())
                .picName(request.getPicName())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .city(request.getCity())
                .bankName(request.getBankName())
                .bankAccountNumber(request.getBankAccountNumber())
                .bankAccountHolder(request.getBankAccountHolder())
                .paymentTerm(request.getPaymentTerm() != null && !request.getPaymentTerm().isBlank()
                        ? request.getPaymentTerm()
                        : "CASH")
                .notes(request.getNotes())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        Supplier saved = supplierRepository.save(supplier);
        log.info("Created new supplier: {} ({})", saved.getName(), saved.getCode());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<SupplierResponse> getAllSuppliers(int page, int size, String search, Boolean isActive, String sortBy, String sortDir) {
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir != null ? sortDir : "DESC"),
                (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Supplier> supplierPage = supplierRepository.searchSuppliers(
                (search != null && !search.isBlank()) ? search.trim() : null,
                isActive,
                pageable);

        return supplierPage.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public SupplierResponse getSupplierById(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan dengan id: " + id));
        return mapToResponse(supplier);
    }

    @Transactional
    public SupplierResponse updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan dengan id: " + id));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (supplierRepository.existsByCodeAndIdNot(newCode, id)) {
                throw new IllegalArgumentException("Kode supplier '" + newCode + "' sudah digunakan oleh supplier lain.");
            }
            supplier.setCode(newCode);
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            supplier.setName(request.getName().trim());
        }
        supplier.setPicName(request.getPicName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setCity(request.getCity());
        supplier.setBankName(request.getBankName());
        supplier.setBankAccountNumber(request.getBankAccountNumber());
        supplier.setBankAccountHolder(request.getBankAccountHolder());
        if (request.getPaymentTerm() != null) {
            supplier.setPaymentTerm(request.getPaymentTerm());
        }
        supplier.setNotes(request.getNotes());
        if (request.getIsActive() != null) {
            supplier.setIsActive(request.getIsActive());
        }

        Supplier updated = supplierRepository.save(supplier);
        log.info("Updated supplier: {} ({})", updated.getName(), updated.getCode());
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan dengan id: " + id));
        supplierRepository.delete(supplier);
        log.info("Deleted supplier id: {}", id);
    }

    @Transactional
    public SupplierResponse toggleActiveStatus(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan dengan id: " + id));
        supplier.setIsActive(!Boolean.TRUE.equals(supplier.getIsActive()));
        Supplier saved = supplierRepository.save(supplier);
        log.info("Toggled active status for supplier {}: {}", saved.getCode(), saved.getIsActive());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSupplierStats() {
        long total = supplierRepository.count();
        long active = supplierRepository.countByIsActiveTrue();
        long inactive = supplierRepository.countByIsActiveFalse();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("active", active);
        stats.put("inactive", inactive);
        return stats;
    }

    private synchronized String generateSupplierCode() {
        long count = supplierRepository.count() + 1;
        String code = String.format("SUP-%03d", count);
        int attempts = 0;
        while (supplierRepository.existsByCode(code) && attempts < 100) {
            count++;
            code = String.format("SUP-%03d", count);
            attempts++;
        }
        return code;
    }

    public SupplierResponse mapToResponse(Supplier s) {
        return SupplierResponse.builder()
                .id(s.getId())
                .code(s.getCode())
                .name(s.getName())
                .picName(s.getPicName())
                .phone(s.getPhone())
                .email(s.getEmail())
                .address(s.getAddress())
                .city(s.getCity())
                .bankName(s.getBankName())
                .bankAccountNumber(s.getBankAccountNumber())
                .bankAccountHolder(s.getBankAccountHolder())
                .paymentTerm(s.getPaymentTerm())
                .notes(s.getNotes())
                .isActive(s.getIsActive())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
