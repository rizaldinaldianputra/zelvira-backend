package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.PricingFormula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricingFormulaRepository extends JpaRepository<PricingFormula, Long> {
    Optional<PricingFormula> findFirstByIsDefaultTrue();

    Optional<PricingFormula> findByCode(String code);

    boolean existsByCode(String code);
}
