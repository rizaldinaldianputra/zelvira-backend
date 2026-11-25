package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
    List<ProductVariant> findByProductId(Long productId);

    List<ProductVariant> findByProductIdAndIsActiveTrue(Long productId);

    Optional<ProductVariant> findBySku(String sku);

    void deleteByProductId(Long productId);

    @Query(value = """
        SELECT (
            EXISTS(SELECT 1 FROM flash_sale_item WHERE variant_id = :variantId)
            OR EXISTS(SELECT 1 FROM order_item WHERE variant_id = :variantId)
            OR EXISTS(SELECT 1 FROM purchase_order_items WHERE variant_id = :variantId)
            OR EXISTS(SELECT 1 FROM goods_receipt_items WHERE variant_id = :variantId)
            OR EXISTS(SELECT 1 FROM product_pricing_profiles WHERE variant_id = :variantId)
        )
        """, nativeQuery = true)
    boolean isVariantReferenced(@Param("variantId") Long variantId);
}
