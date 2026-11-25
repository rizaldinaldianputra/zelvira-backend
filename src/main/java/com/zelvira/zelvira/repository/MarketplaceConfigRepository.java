package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.MarketplaceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketplaceConfigRepository extends JpaRepository<MarketplaceConfig, Long> {
    Optional<MarketplaceConfig> findByMarketplaceTypeAndShopId(String marketplaceType, String shopId);

    Optional<MarketplaceConfig> findFirstByMarketplaceTypeAndIsActiveTrue(String marketplaceType);

    List<MarketplaceConfig> findAllByMarketplaceType(String marketplaceType);

    List<MarketplaceConfig> findAllByMarketplaceTypeAndIsActiveTrue(String marketplaceType);
}
