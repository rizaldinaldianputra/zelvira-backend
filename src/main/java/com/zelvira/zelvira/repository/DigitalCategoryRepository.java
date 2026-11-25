package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.DigitalCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DigitalCategoryRepository extends JpaRepository<DigitalCategory, Long> {
    List<DigitalCategory> findByIsPostpaidOrderBySortOrderAsc(Boolean isPostpaid);

    List<DigitalCategory> findByIsPostpaidAndIsActiveOrderBySortOrderAsc(Boolean isPostpaid, Boolean isActive);

    List<DigitalCategory> findByIsActiveOrderBySortOrderAsc(Boolean isActive);

    List<DigitalCategory> findAllByOrderBySortOrderAsc();

    Optional<DigitalCategory> findByCodeAndIsPostpaid(String code, Boolean isPostpaid);
}
