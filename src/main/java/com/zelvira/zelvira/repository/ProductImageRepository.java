package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByProductId(Long productId);

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    void deleteByProductId(Long productId);

    List<ProductImage> findByVariantId(Long variantId);

    void deleteByProductIdAndVariantId(Long productId, Long variantId);

    void deleteByProductIdAndVariantIdIsNull(Long productId);
}
