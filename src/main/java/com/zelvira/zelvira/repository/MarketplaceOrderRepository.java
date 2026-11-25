package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.MarketplaceOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceOrderRepository extends JpaRepository<MarketplaceOrder, Long> {
    Optional<MarketplaceOrder> findByMarketplaceTypeAndMarketplaceOrderId(String marketplaceType,
            String marketplaceOrderId);

    List<MarketplaceOrder> findAllByMarketplaceTypeOrderByOrderCreatedAtDesc(String marketplaceType);

    List<MarketplaceOrder> findAllByOrderByOrderCreatedAtDesc();

    List<MarketplaceOrder> findAllByMarketplaceTypeAndStockDeductedFalse(String marketplaceType);
}
