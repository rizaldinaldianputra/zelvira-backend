package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("SELECT oi.productId, SUM(oi.quantity) AS totalSold FROM OrderItem oi GROUP BY oi.productId ORDER BY totalSold DESC")
    List<Object[]> findTopSellingProductIds(Pageable pageable);
}
