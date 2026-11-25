package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.PurchaseOrderItemRequest;
import com.zelvira.zelvira.dto.PurchaseOrderItemResponse;
import com.zelvira.zelvira.dto.PurchaseOrderRequest;
import com.zelvira.zelvira.dto.PurchaseOrderResponse;
import com.zelvira.zelvira.entity.ProductVariant;
import com.zelvira.zelvira.entity.PurchaseOrder;
import com.zelvira.zelvira.entity.PurchaseOrderItem;
import com.zelvira.zelvira.entity.Supplier;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.ProductVariantRepository;
import com.zelvira.zelvira.repository.PurchaseOrderRepository;
import com.zelvira.zelvira.repository.SupplierRepository;
import com.zelvira.zelvira.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProcurementWorkflowService procurementWorkflowService;

    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }

    @Transactional
    public PurchaseOrderResponse createPurchaseOrder(PurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan dengan id: " + request.getSupplierId()));

        String poNumber = request.getPoNumber();
        if (poNumber == null || poNumber.isBlank()) {
            poNumber = generatePoNumber();
        } else {
            poNumber = poNumber.trim().toUpperCase();
            if (purchaseOrderRepository.existsByPoNumber(poNumber)) {
                throw new IllegalArgumentException("Nomor PO '" + poNumber + "' sudah digunakan.");
            }
        }

        PurchaseOrder po = PurchaseOrder.builder()
                .poNumber(poNumber)
                .supplier(supplier)
                .orderDate(request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .status(request.getStatus() != null && !request.getStatus().isBlank() ? request.getStatus().toUpperCase() : "DRAFT")
                .paymentStatus(request.getPaymentStatus() != null && !request.getPaymentStatus().isBlank() ? request.getPaymentStatus().toUpperCase() : "UNPAID")
                .paymentTerm(request.getPaymentTerm() != null ? request.getPaymentTerm() : supplier.getPaymentTerm())
                .taxAmount(request.getTaxAmount() != null ? request.getTaxAmount() : BigDecimal.ZERO)
                .shippingCost(request.getShippingCost() != null ? request.getShippingCost() : BigDecimal.ZERO)
                .discountAmount(request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO)
                .notes(request.getNotes())
                .createdBy(getCurrentUser())
                .items(new ArrayList<>())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (PurchaseOrderItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product Variant tidak ditemukan dengan id: " + itemReq.getVariantId()));

            BigDecimal itemSubtotal = itemReq.getUnitCost().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemSubtotal);

            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .purchaseOrder(po)
                    .variant(variant)
                    .quantity(itemReq.getQuantity())
                    .receivedQuantity(0)
                    .unitCost(itemReq.getUnitCost())
                    .subtotal(itemSubtotal)
                    .notes(itemReq.getNotes())
                    .build();

            po.getItems().add(item);
        }

        BigDecimal total = subtotal
                .add(po.getTaxAmount())
                .add(po.getShippingCost())
                .subtract(po.getDiscountAmount());

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        po.setSubtotal(subtotal);
        po.setTotalAmount(total);

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        procurementWorkflowService.startPoApprovalProcess(saved);
        log.info("Created Purchase Order: {} for supplier: {}, initiated Camunda approval process", saved.getPoNumber(), supplier.getName());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> getAllPurchaseOrders(
            int page,
            int size,
            String query,
            Long supplierId,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String sortBy,
            String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir != null ? sortDir : "DESC"),
                (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<PurchaseOrder> poPage = purchaseOrderRepository.searchPurchaseOrders(
                (query != null && !query.isBlank()) ? query.trim() : null,
                supplierId,
                (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null,
                startDate,
                endDate,
                pageable);

        return poPage.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrderById(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order tidak ditemukan dengan id: " + id));
        return mapToResponse(po);
    }

    @Transactional
    public PurchaseOrderResponse updatePurchaseOrder(Long id, PurchaseOrderRequest request) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order tidak ditemukan dengan id: " + id));

        if ("RECEIVED".equalsIgnoreCase(po.getStatus())) {
            throw new IllegalStateException("Pesanan dengan status RECEIVED tidak dapat diubah.");
        }

        if (request.getSupplierId() != null && !request.getSupplierId().equals(po.getSupplier().getId())) {
            Supplier supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan dengan id: " + request.getSupplierId()));
            po.setSupplier(supplier);
        }

        if (request.getOrderDate() != null) {
            po.setOrderDate(request.getOrderDate());
        }
        po.setExpectedDeliveryDate(request.getExpectedDeliveryDate());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            po.setStatus(request.getStatus().toUpperCase());
        }
        if (request.getPaymentStatus() != null && !request.getPaymentStatus().isBlank()) {
            po.setPaymentStatus(request.getPaymentStatus().toUpperCase());
        }
        if (request.getPaymentTerm() != null) {
            po.setPaymentTerm(request.getPaymentTerm());
        }
        if (request.getTaxAmount() != null) {
            po.setTaxAmount(request.getTaxAmount());
        }
        if (request.getShippingCost() != null) {
            po.setShippingCost(request.getShippingCost());
        }
        if (request.getDiscountAmount() != null) {
            po.setDiscountAmount(request.getDiscountAmount());
        }
        po.setNotes(request.getNotes());

        // Update items if provided and PO hasn't received anything yet
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            boolean hasReceivedItems = po.getItems().stream().anyMatch(i -> i.getReceivedQuantity() > 0);
            if (hasReceivedItems) {
                throw new IllegalStateException("Item pesanan tidak dapat diubah karena sebagian barang telah diterima.");
            }

            po.getItems().clear();
            BigDecimal subtotal = BigDecimal.ZERO;

            for (PurchaseOrderItemRequest itemReq : request.getItems()) {
                ProductVariant variant = productVariantRepository.findById(itemReq.getVariantId())
                        .orElseThrow(() -> new ResourceNotFoundException("Product Variant tidak ditemukan dengan id: " + itemReq.getVariantId()));

                BigDecimal itemSubtotal = itemReq.getUnitCost().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                subtotal = subtotal.add(itemSubtotal);

                PurchaseOrderItem item = PurchaseOrderItem.builder()
                        .purchaseOrder(po)
                        .variant(variant)
                        .quantity(itemReq.getQuantity())
                        .receivedQuantity(0)
                        .unitCost(itemReq.getUnitCost())
                        .subtotal(itemSubtotal)
                        .notes(itemReq.getNotes())
                        .build();

                po.getItems().add(item);
            }

            BigDecimal total = subtotal
                    .add(po.getTaxAmount())
                    .add(po.getShippingCost())
                    .subtract(po.getDiscountAmount());

            if (total.compareTo(BigDecimal.ZERO) < 0) {
                total = BigDecimal.ZERO;
            }

            po.setSubtotal(subtotal);
            po.setTotalAmount(total);
        }

        PurchaseOrder updated = purchaseOrderRepository.save(po);
        log.info("Updated Purchase Order: {}", updated.getPoNumber());
        return mapToResponse(updated);
    }

    @Transactional
    public PurchaseOrderResponse updateStatus(Long id, String status) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order tidak ditemukan dengan id: " + id));

        String newStatus = status.trim().toUpperCase();
        po.setStatus(newStatus);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        log.info("Updated status for PO {}: {}", saved.getPoNumber(), newStatus);
        return mapToResponse(saved);
    }

    @Transactional
    public void deletePurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order tidak ditemukan dengan id: " + id));

        boolean hasReceivedItems = po.getItems().stream().anyMatch(i -> i.getReceivedQuantity() > 0);
        if (hasReceivedItems) {
            throw new IllegalStateException("Pesanan tidak dapat dihapus karena sudah ada penerimaan barang.");
        }

        purchaseOrderRepository.delete(po);
        log.info("Deleted Purchase Order id: {}", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPurchaseOrderStats() {
        long total = purchaseOrderRepository.count();
        long draft = purchaseOrderRepository.countByStatus("DRAFT");
        long ordered = purchaseOrderRepository.countByStatus("ORDERED");
        long partiallyReceived = purchaseOrderRepository.countByStatus("PARTIALLY_RECEIVED");
        long received = purchaseOrderRepository.countByStatus("RECEIVED");
        long cancelled = purchaseOrderRepository.countByStatus("CANCELLED");

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("draft", draft);
        stats.put("ordered", ordered);
        stats.put("partiallyReceived", partiallyReceived);
        stats.put("received", received);
        stats.put("cancelled", cancelled);
        return stats;
    }

    private synchronized String generatePoNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = purchaseOrderRepository.count() + 1;
        String poNumber = String.format("PO-%s-%04d", datePrefix, count);
        int attempts = 0;
        while (purchaseOrderRepository.existsByPoNumber(poNumber) && attempts < 100) {
            count++;
            poNumber = String.format("PO-%s-%04d", datePrefix, count);
            attempts++;
        }
        return poNumber;
    }

    @Transactional
    public PurchaseOrderResponse approvePurchaseOrder(Long id) {
        PurchaseOrder approved = procurementWorkflowService.approvePurchaseOrder(id, getCurrentUser());
        return mapToResponse(approved);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> getApprovedPurchaseOrders() {
        // Can be called by Goods Receipt (status APPROVED or PARTIAL)
        List<PurchaseOrder> pos = purchaseOrderRepository.findByStatusInOrderByCreatedAtDesc(List.of("APPROVED", "PARTIAL"));
        return pos.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getPurchaseOrderByPoNumber(String poNumber) {
        PurchaseOrder po = purchaseOrderRepository.findByPoNumber(poNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order dengan nomor " + poNumber + " tidak ditemukan"));
        return mapToResponse(po);
    }

    public PurchaseOrderResponse mapToResponse(PurchaseOrder po) {
        List<PurchaseOrderItemResponse> itemResponses = po.getItems().stream().map(item -> {
            ProductVariant v = item.getVariant();
            String prodName = v.getProduct() != null ? v.getProduct().getName() : "-";
            int remaining = Math.max(0, item.getQuantity() - item.getReceivedQuantity());

            return PurchaseOrderItemResponse.builder()
                    .id(item.getId())
                    .variantId(v.getId())
                    .sku(v.getSku())
                    .productName(prodName)
                    .size(v.getSize())
                    .color(v.getColor())
                    .currentStock(v.getStock())
                    .quantity(item.getQuantity())
                    .receivedQuantity(item.getReceivedQuantity())
                    .remainingQuantity(remaining)
                    .unitCost(item.getUnitCost())
                    .subtotal(item.getSubtotal())
                    .notes(item.getNotes())
                    .build();
        }).collect(Collectors.toList());

        int totalOrderedQty = po.getItems().stream().mapToInt(PurchaseOrderItem::getQuantity).sum();
        int totalReceivedQty = po.getItems().stream().mapToInt(PurchaseOrderItem::getReceivedQuantity).sum();

        return PurchaseOrderResponse.builder()
                .id(po.getId())
                .poNumber(po.getPoNumber())
                .supplierId(po.getSupplier().getId())
                .supplierCode(po.getSupplier().getCode())
                .supplierName(po.getSupplier().getName())
                .supplierPhone(po.getSupplier().getPhone())
                .orderDate(po.getOrderDate())
                .expectedDeliveryDate(po.getExpectedDeliveryDate())
                .status(po.getStatus())
                .paymentStatus(po.getPaymentStatus())
                .paymentTerm(po.getPaymentTerm())
                .subtotal(po.getSubtotal())
                .taxAmount(po.getTaxAmount())
                .shippingCost(po.getShippingCost())
                .discountAmount(po.getDiscountAmount())
                .totalAmount(po.getTotalAmount())
                .notes(po.getNotes())
                .createdBy(po.getCreatedBy())
                .approvedBy(po.getApprovedBy())
                .approvedAt(po.getApprovedAt())
                .totalItems(po.getItems().size())
                .totalOrderedQty(totalOrderedQty)
                .totalReceivedQty(totalReceivedQty)
                .items(itemResponses)
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }
}
