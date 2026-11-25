package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {
    List<Faq> findAllByIsActiveTrueOrderByIdAsc();

    List<Faq> findAllByOrderByIdAsc();
}
