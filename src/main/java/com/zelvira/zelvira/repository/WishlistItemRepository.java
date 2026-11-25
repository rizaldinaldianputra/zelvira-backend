package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.Product;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByUser(User user);

    Optional<WishlistItem> findByUserAndProduct(User user, Product product);

    boolean existsByUserAndProduct(User user, Product product);
}
