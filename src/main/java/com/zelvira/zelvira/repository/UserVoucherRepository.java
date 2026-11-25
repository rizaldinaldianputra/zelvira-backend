package com.zelvira.zelvira.repository;

import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.entity.UserVoucher;
import com.zelvira.zelvira.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserVoucherRepository extends JpaRepository<UserVoucher, Long> {
    Optional<UserVoucher> findByUserAndVoucher(User user, Voucher voucher);

    List<UserVoucher> findByUser(User user);

    boolean existsByUserAndVoucher(User user, Voucher voucher);
}
