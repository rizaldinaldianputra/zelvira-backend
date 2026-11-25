package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.FlashSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    @Query("SELECT f FROM FlashSale f WHERE f.isActive = true AND f.endTime >= :now")
    List<FlashSale> findActiveFlashSales(LocalDateTime now);

    List<FlashSale> findByPlatformOrderByCreatedAtDesc(String platform);

    @Query("SELECT f FROM FlashSale f WHERE f.platform = :platform AND f.isActive = true AND f.endTime >= :now")
    List<FlashSale> findActiveFlashSalesByPlatform(String platform, LocalDateTime now);
}
