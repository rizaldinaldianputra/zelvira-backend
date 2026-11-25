package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.DigitalOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DigitalOrderRepository extends JpaRepository<DigitalOrder, Long> {
    Optional<DigitalOrder> findByOrderNumber(String orderNumber);

    List<DigitalOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<DigitalOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
