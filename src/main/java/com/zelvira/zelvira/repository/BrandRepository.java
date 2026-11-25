package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findBySlug(String slug);

    Page<Brand> findByNameContainingIgnoreCase(String name, Pageable pageable);

    boolean existsBySlug(String slug);
}
