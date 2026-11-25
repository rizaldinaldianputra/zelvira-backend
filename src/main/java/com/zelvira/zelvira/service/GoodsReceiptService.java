package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.GoodsReceiptItemRequest;
import com.zelvira.zelvira.dto.GoodsReceiptItemResponse;
import com.zelvira.zelvira.dto.GoodsReceiptRequest;
import com.zelvira.zelvira.dto.GoodsReceiptResponse;
import com.zelvira.zelvira.entity.*;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.*;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ProcurementWorkflowService procurementWorkflowService;

    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }

    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(GoodsReceiptRequest request) {
        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier tidak ditemukan dengan id: " + request.getSupplierId()));

        PurchaseOrder purchaseOrder = null;
        if (request.getPurchaseOrderId() != null) {
            purchaseOrder = purchaseOrderRepository.findById(request.getPurchaseOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Purchase Order tidak ditemukan dengan id: " + request.getPurchaseOrderId()));

            if (!"APPROVED".equalsIgnoreCase(purchaseOrder.getStatus()) && !"PARTIAL".equalsIgnoreCase(purchaseOrder.getStatus())) {
                throw new IllegalStateException("Penerimaan barang (GR) hanya dapat dibuat untuk Purchase Order dengan status APPROVED atau PARTIAL (Status saat ini: " + purchaseOrder.getStatus() + ")");
            }
        }

        String receiptNumber = request.getReceiptNumber();
        if (receiptNumber == null || receiptNumber.isBlank()) {
            receiptNumber = generateReceiptNumber();
        } else {
            receiptNumber = receiptNumber.trim().toUpperCase();
            if (goodsReceiptRepository.existsByReceiptNumber(receiptNumber)) {
                throw new IllegalArgumentException("Nomor penerimaan '" + receiptNumber + "' sudah digunakan.");
            }
        }

        String currentUser = getCurrentUser();

        GoodsReceipt receipt = GoodsReceipt.builder()
                .receiptNumber(receiptNumber)
                .purchaseOrder(purchaseOrder)
                .supplier(supplier)
                .receiptDate(request.getReceiptDate() != null ? request.getReceiptDate() : LocalDate.now())
                .doNumber(request.getDoNumber())
                .receivedBy(currentUser)
                .status("DRAFT")
                .notes(request.getNotes())
                .items(new ArrayList<>())
                .build();

        for (GoodsReceiptItemRequest itemReq : request.getItems()) {
            ProductVariant variant = productVariantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product Variant tidak ditemukan dengan id: " + itemReq.getVariantId()));

            PurchaseOrderItem poItem = null;
            if (itemReq.getPurchaseOrderItemId() != null) {
                poItem = purchaseOrderItemRepository.findById(itemReq.getPurchaseOrderItemId())
                        .orElse(null);
            }

            // Create GoodsReceiptItem in DRAFT status (stock will mutate when approved)
            GoodsReceiptItem receiptItem = GoodsReceiptItem.builder()
                    .goodsReceipt(receipt)
                    .purchaseOrderItem(poItem)
                    .variant(variant)
                    .quantityReceived(itemReq.getQuantityReceived())
                    .quantityRejected(itemReq.getQuantityRejected() != null ? itemReq.getQuantityRejected() : 0)
                    .unitCost(itemReq.getUnitCost() != null ? itemReq.getUnitCost() : (poItem != null ? poItem.getUnitCost() : variant.getCostPrice()))
                    .notes(itemReq.getNotes())
                    .build();

            receipt.getItems().add(receiptItem);
        }

        GoodsReceipt saved = goodsReceiptRepository.save(receipt);
        log.info("Created Goods Receipt in DRAFT: {} for supplier: {}, total items: {}",
                saved.getReceiptNumber(), supplier.getName(), saved.getItems().size());
        return mapToResponse(saved);
    }

    @Transactional
    public GoodsReceiptResponse approveGoodsReceipt(Long id) {
        GoodsReceipt approved = procurementWorkflowService.approveGoodsReceipt(id, getCurrentUser());
        return mapToResponse(approved);
    }

    private void updatePurchaseOrderStatus(PurchaseOrder po) {
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findByPurchaseOrderId(po.getId());
        if (items.isEmpty()) return;

        boolean allFulfilled = true;
        boolean anyReceived = false;

        for (PurchaseOrderItem item : items) {
            int received = item.getReceivedQuantity() != null ? item.getReceivedQuantity() : 0;
            if (received > 0) {
                anyReceived = true;
            }
            if (received < item.getQuantity()) {
                allFulfilled = false;
            }
        }

        if (allFulfilled) {
            po.setStatus("RECEIVED");
        } else if (anyReceived) {
            po.setStatus("PARTIALLY_RECEIVED");
        }
        purchaseOrderRepository.save(po);
        log.info("Updated PO {} status to: {}", po.getPoNumber(), po.getStatus());
    }

    @Transactional(readOnly = true)
    public Page<GoodsReceiptResponse> getAllGoodsReceipts(
            int page,
            int size,
            String query,
            Long supplierId,
            Long poId,
            LocalDate startDate,
            LocalDate endDate,
            String sortBy,
            String sortDir) {

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir != null ? sortDir : "DESC"),
                (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<GoodsReceipt> receiptPage = goodsReceiptRepository.searchGoodsReceipts(
                (query != null && !query.isBlank()) ? query.trim() : null,
                supplierId,
                poId,
                startDate,
                endDate,
                pageable);

        return receiptPage.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public GoodsReceiptResponse getGoodsReceiptById(Long id) {
        GoodsReceipt receipt = goodsReceiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goods Receipt tidak ditemukan dengan id: " + id));
        return mapToResponse(receipt);
    }

    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> getReceiptsByPurchaseOrder(Long poId) {
        return goodsReceiptRepository.findByPurchaseOrderId(poId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private synchronized String generateReceiptNumber() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = goodsReceiptRepository.count() + 1;
        String receiptNumber = String.format("GR-%s-%04d", datePrefix, count);
        int attempts = 0;
        while (goodsReceiptRepository.existsByReceiptNumber(receiptNumber) && attempts < 100) {
            count++;
            receiptNumber = String.format("GR-%s-%04d", datePrefix, count);
            attempts++;
        }
        return receiptNumber;
    }

    public GoodsReceiptResponse mapToResponse(GoodsReceipt gr) {
        List<GoodsReceiptItemResponse> itemResponses = gr.getItems().stream().map(item -> {
            ProductVariant v = item.getVariant();
            String prodName = v.getProduct() != null ? v.getProduct().getName() : "-";

            return GoodsReceiptItemResponse.builder()
                    .id(item.getId())
                    .purchaseOrderItemId(item.getPurchaseOrderItem() != null ? item.getPurchaseOrderItem().getId() : null)
                    .variantId(v.getId())
                    .sku(v.getSku())
                    .productName(prodName)
                    .size(v.getSize())
                    .color(v.getColor())
                    .quantityReceived(item.getQuantityReceived())
                    .quantityRejected(item.getQuantityRejected())
                    .unitCost(item.getUnitCost())
                    .notes(item.getNotes())
                    .build();
        }).collect(Collectors.toList());

        int totalReceivedQty = gr.getItems().stream().mapToInt(GoodsReceiptItem::getQuantityReceived).sum();
        int totalRejectedQty = gr.getItems().stream().mapToInt(GoodsReceiptItem::getQuantityRejected).sum();

        return GoodsReceiptResponse.builder()
                .id(gr.getId())
                .receiptNumber(gr.getReceiptNumber())
                .purchaseOrderId(gr.getPurchaseOrder() != null ? gr.getPurchaseOrder().getId() : null)
                .poNumber(gr.getPurchaseOrder() != null ? gr.getPurchaseOrder().getPoNumber() : null)
                .supplierId(gr.getSupplier().getId())
                .supplierCode(gr.getSupplier().getCode())
                .supplierName(gr.getSupplier().getName())
                .receiptDate(gr.getReceiptDate())
                .doNumber(gr.getDoNumber())
                .receivedBy(gr.getReceivedBy())
                .approvedBy(gr.getApprovedBy())
                .approvedAt(gr.getApprovedAt())
                .status(gr.getStatus())
                .notes(gr.getNotes())
                .totalItems(gr.getItems().size())
                .totalReceivedQty(totalReceivedQty)
                .totalRejectedQty(totalRejectedQty)
                .items(itemResponses)
                .createdAt(gr.getCreatedAt())
                .updatedAt(gr.getUpdatedAt())
                .build();
    }
}
