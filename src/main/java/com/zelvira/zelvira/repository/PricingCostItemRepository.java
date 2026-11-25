package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.PricingCostItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PricingCostItemRepository extends JpaRepository<PricingCostItem, Long> {
    List<PricingCostItem> findAllByOrderByCategoryAscNameAsc();

    List<PricingCostItem> findByIsActiveTrueOrderByCategoryAscNameAsc();

    Optional<PricingCostItem> findByCode(String code);

    boolean existsByCode(String code);
}
