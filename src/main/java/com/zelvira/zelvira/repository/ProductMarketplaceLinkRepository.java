package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.ProductMarketplaceLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductMarketplaceLinkRepository extends JpaRepository<ProductMarketplaceLink, Long> {
    List<ProductMarketplaceLink> findAllByProductId(Long productId);

    Optional<ProductMarketplaceLink> findByProductIdAndMarketplaceType(Long productId, String marketplaceType);

    Optional<ProductMarketplaceLink> findByMarketplaceTypeAndMarketplaceProductId(String marketplaceType,
            String marketplaceProductId);

    List<ProductMarketplaceLink> findAllByMarketplaceType(String marketplaceType);
}
