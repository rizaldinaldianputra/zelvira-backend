package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.ProductPricingProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductPricingProfileRepository extends JpaRepository<ProductPricingProfile, Long> {
    Optional<ProductPricingProfile> findByProductIdAndVariantId(Long productId, Long variantId);

    Optional<ProductPricingProfile> findFirstByProductIdAndVariantIdIsNull(Long productId);

    List<ProductPricingProfile> findAllByProductId(Long productId);
}
