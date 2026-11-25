package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.GoodsReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {

       Optional<GoodsReceipt> findByReceiptNumber(String receiptNumber);

       boolean existsByReceiptNumber(String receiptNumber);

       List<GoodsReceipt> findByPurchaseOrderId(Long purchaseOrderId);

       @Query("SELECT gr FROM GoodsReceipt gr WHERE " +
                     "(:query IS NULL OR :query = '' OR " +
                     "LOWER(gr.receiptNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                     "LOWER(gr.doNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                     "LOWER(gr.supplier.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                     "(gr.purchaseOrder IS NOT NULL AND LOWER(gr.purchaseOrder.poNumber) LIKE LOWER(CONCAT('%', :query, '%')))) AND "
                     +
                     "(:supplierId IS NULL OR gr.supplier.id = :supplierId) AND " +
                     "(:poId IS NULL OR (gr.purchaseOrder IS NOT NULL AND gr.purchaseOrder.id = :poId)) AND " +
                     "(:startDate IS NULL OR gr.receiptDate >= :startDate) AND " +
                     "(:endDate IS NULL OR gr.receiptDate <= :endDate)")
       Page<GoodsReceipt> searchGoodsReceipts(
                     @Param("query") String query,
                     @Param("supplierId") Long supplierId,
                     @Param("poId") Long poId,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate,
                     Pageable pageable);
}
