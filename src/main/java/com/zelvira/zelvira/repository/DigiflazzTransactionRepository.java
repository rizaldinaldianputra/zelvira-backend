package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.DigiflazzTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DigiflazzTransactionRepository extends JpaRepository<DigiflazzTransaction, Long> {

    Optional<DigiflazzTransaction> findByRefId(String refId);

}
