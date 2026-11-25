package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.CartItem;
import com.zelvira.zelvira.entity.ProductVariant;
import com.zelvira.zelvira.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);

    Optional<CartItem> findByUserAndProductVariant(User user, ProductVariant productVariant);

    void deleteByUser(User user);

    void deleteByProductVariantId(Long productVariantId);
}
