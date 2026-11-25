package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

        Optional<PurchaseOrder> findByPoNumber(String poNumber);

        boolean existsByPoNumber(String poNumber);

        List<PurchaseOrder> findBySupplierIdOrderByCreatedAtDesc(Long supplierId);

        List<PurchaseOrder> findByStatusInOrderByCreatedAtDesc(List<String> statuses);

        @Query("SELECT po FROM PurchaseOrder po WHERE " +
                        "(:query IS NULL OR :query = '' OR " +
                        "LOWER(po.poNumber) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(po.supplier.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(po.supplier.code) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
                        "(:supplierId IS NULL OR po.supplier.id = :supplierId) AND " +
                        "(:status IS NULL OR po.status = :status) AND " +
                        "(:startDate IS NULL OR po.orderDate >= :startDate) AND " +
                        "(:endDate IS NULL OR po.orderDate <= :endDate)")
        Page<PurchaseOrder> searchPurchaseOrders(
                        @Param("query") String query,
                        @Param("supplierId") Long supplierId,
                        @Param("status") String status,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        Pageable pageable);

        long countByStatus(String status);
}
