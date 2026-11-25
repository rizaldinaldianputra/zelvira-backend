package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByOrderNumberIgnoreCase(String orderNumber);

    List<Order> findByUserId(Long userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Order> findAllByCreatedAtAfter(LocalDateTime dateTime);

    List<Order> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<Order> findTop10ByOrderByCreatedAtDesc();
}
