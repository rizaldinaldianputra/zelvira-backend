package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.FlashSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {
    List<FlashSaleItem> findByFlashSaleId(Long flashSaleId);

    void deleteByFlashSaleId(Long flashSaleId);
}
