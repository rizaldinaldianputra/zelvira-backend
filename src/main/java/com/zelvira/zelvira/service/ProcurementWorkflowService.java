package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.*;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementWorkflowService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockTransactionRepository stockTransactionRepository;

    public void startPoApprovalProcess(PurchaseOrder po) {
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("poId", po.getId());
            variables.put("poNumber", po.getPoNumber());
            variables.put("supplierName", po.getSupplier() != null ? po.getSupplier().getName() : "-");
            variables.put("totalAmount", po.getTotalAmount());
            variables.put("createdBy", po.getCreatedBy());

            runtimeService.startProcessInstanceByKey("procurement-po-process", String.valueOf(po.getId()), variables);
            log.info("Started Camunda procurement-po-process for PO: {}", po.getPoNumber());
        } catch (Exception e) {
            log.error("Failed to start Camunda process for PO {}: {}", po.getPoNumber(), e.getMessage());
        }
    }

    @Transactional
    public PurchaseOrder approvePurchaseOrder(Long poId, String approvedBy) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order tidak ditemukan dengan id: " + poId));

        if (!"DRAFT".equalsIgnoreCase(po.getStatus())) {
            throw new IllegalStateException("Hanya Purchase Order dengan status DRAFT yang dapat disetujui (Status saat ini: " + po.getStatus() + ")");
        }

        // Complete Camunda task if exists
        try {
            Task task = taskService.createTaskQuery()
                    .processDefinitionKey("procurement-po-process")
                    .processInstanceBusinessKey(String.valueOf(poId))
                    .singleResult();

            if (task != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("isApproved", true);
                vars.put("approvedBy", approvedBy);
                taskService.complete(task.getId(), vars);
                log.info("Completed Camunda task {} for PO {}", task.getId(), po.getPoNumber());
            }
        } catch (Exception e) {
            log.warn("Could not complete Camunda task for PO {}: {}", po.getPoNumber(), e.getMessage());
        }

        po.setStatus("APPROVED");
        po.setApprovedBy(approvedBy);
        po.setApprovedAt(LocalDateTime.now());

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        log.info("Purchase Order {} successfully APPROVED by {}", saved.getPoNumber(), approvedBy);
        return saved;
    }

    @Transactional
    public PurchaseOrder rejectPurchaseOrder(Long poId, String rejectedBy, String reason) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order tidak ditemukan dengan id: " + poId));

        if (!"DRAFT".equalsIgnoreCase(po.getStatus())) {
            throw new IllegalStateException("Hanya Purchase Order dengan status DRAFT yang dapat ditolak.");
        }

        try {
            Task task = taskService.createTaskQuery()
                    .processDefinitionKey("procurement-po-process")
                    .processInstanceBusinessKey(String.valueOf(poId))
                    .singleResult();

            if (task != null) {
                Map<String, Object> vars = new HashMap<>();
                vars.put("isApproved", false);
                vars.put("rejectReason", reason);
                vars.put("rejectedBy", rejectedBy);
                taskService.complete(task.getId(), vars);
            }
        } catch (Exception e) {
            log.warn("Could not reject Camunda task for PO {}: {}", po.getPoNumber(), e.getMessage());
        }

        po.setStatus("CANCELLED");
        po.setNotes(po.getNotes() != null ? po.getNotes() + "\n[REJECTED: " + reason + "]" : "[REJECTED: " + reason + "]");
        return purchaseOrderRepository.save(po);
    }

    @Transactional
    public GoodsReceipt approveGoodsReceipt(Long grId, String approvedBy) {
        GoodsReceipt gr = goodsReceiptRepository.findById(grId)
                .orElseThrow(() -> new ResourceNotFoundException("Goods Receipt tidak ditemukan dengan id: " + grId));

        if ("APPROVED".equalsIgnoreCase(gr.getStatus())) {
            throw new IllegalStateException("Goods Receipt ini sudah disetujui sebelumnya.");
        }
        if ("CANCELLED".equalsIgnoreCase(gr.getStatus())) {
            throw new IllegalStateException("Goods Receipt yang dibatalkan tidak dapat disetujui.");
        }

        // 1. Mutate inventory stock and create stock transactions for each item
        for (GoodsReceiptItem item : gr.getItems()) {
            ProductVariant variant = item.getVariant();
            int currentStock = variant.getStock() != null ? variant.getStock() : 0;
            int receivedQty = item.getQuantityReceived();
            variant.setStock(currentStock + receivedQty);

            if (item.getUnitCost() != null) {
                variant.setCostPrice(item.getUnitCost());
            }
            productVariantRepository.save(variant);

            // Record StockTransaction
            String stockNotes = "Penerimaan Barang #" + gr.getReceiptNumber();
            if (gr.getPurchaseOrder() != null) {
                stockNotes += " (PO #" + gr.getPurchaseOrder().getPoNumber() + ")";
            }
            if (item.getNotes() != null && !item.getNotes().isBlank()) {
                stockNotes += " - " + item.getNotes();
            }

            StockTransaction stockTx = StockTransaction.builder()
                    .variant(variant)
                    .quantity(receivedQty)
                    .type("ADDITION")
                    .notes(stockNotes)
                    .createdBy(approvedBy)
                    .build();
            stockTransactionRepository.save(stockTx);

            // Update PurchaseOrderItem received quantity
            if (item.getPurchaseOrderItem() != null) {
                PurchaseOrderItem poItem = item.getPurchaseOrderItem();
                int oldReceived = poItem.getReceivedQuantity() != null ? poItem.getReceivedQuantity() : 0;
                poItem.setReceivedQuantity(oldReceived + receivedQty);
                purchaseOrderItemRepository.save(poItem);
            }
        }

        // 2. Mark Goods Receipt as APPROVED
        gr.setStatus("APPROVED");
        gr.setApprovedBy(approvedBy);
        gr.setApprovedAt(LocalDateTime.now());
        GoodsReceipt savedGr = goodsReceiptRepository.save(gr);

        // 3. Update Purchase Order overall status (PARTIAL or COMPLETED)
        PurchaseOrder po = gr.getPurchaseOrder();
        if (po != null) {
            updatePurchaseOrderStatus(po);
        }

        log.info("Goods Receipt {} APPROVED by {}. Stock added to inventory.", savedGr.getReceiptNumber(), approvedBy);
        return savedGr;
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
            po.setStatus("COMPLETED");
        } else if (anyReceived) {
            po.setStatus("PARTIAL");
        }
        purchaseOrderRepository.save(po);
        log.info("Updated PO {} status to: {}", po.getPoNumber(), po.getStatus());
    }
}
