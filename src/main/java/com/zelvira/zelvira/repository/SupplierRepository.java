package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

        Optional<Supplier> findByCode(String code);

        boolean existsByCode(String code);

        boolean existsByCodeAndIdNot(String code, Long id);

        @Query("SELECT s FROM Supplier s WHERE " +
                        "(:query IS NULL OR :query = '' OR " +
                        "LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(s.code) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "LOWER(s.picName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
                        "s.phone LIKE CONCAT('%', :query, '%') OR " +
                        "LOWER(s.city) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
                        "(:isActive IS NULL OR s.isActive = :isActive)")
        Page<Supplier> searchSuppliers(
                        @Param("query") String query,
                        @Param("isActive") Boolean isActive,
                        Pageable pageable);

        long countByIsActiveTrue();

        long countByIsActiveFalse();
}
