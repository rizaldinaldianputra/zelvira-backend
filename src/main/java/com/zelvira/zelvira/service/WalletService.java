package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.WalletResponseDTO;
import com.zelvira.zelvira.dto.WalletTransactionDTO;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.entity.WalletTransaction;
import com.zelvira.zelvira.exception.ResourceNotFoundException;
import com.zelvira.zelvira.repository.UserRepository;
import com.zelvira.zelvira.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class WalletService {

        private final UserRepository userRepository;
        private final WalletTransactionRepository transactionRepository;

        public WalletService(UserRepository userRepository, WalletTransactionRepository transactionRepository) {
                this.userRepository = userRepository;
                this.transactionRepository = transactionRepository;
        }

        public WalletResponseDTO getWalletInfo(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                List<WalletTransaction> transactions = transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);

                List<WalletTransactionDTO> transactionDTOs = transactions.stream()
                                .limit(100) // Recent 100 transactions for complete history
                                .map(this::convertToDTO)
                                .collect(Collectors.toList());

                return WalletResponseDTO.builder()
                                .balance(user.getBalance())
                                .points(user.getPoints())
                                .recentTransactions(transactionDTOs)
                                .build();
        }

        @Transactional
        public void addBalanceOnly(Long userId, Double amount) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                user.setBalance(user.getBalance() + amount);
                userRepository.save(user);
        }

        @Transactional
        public void addBalance(Long userId, Double amount, String description, String referenceId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                user.setBalance(user.getBalance() + amount);
                userRepository.save(user);

                WalletTransaction transaction = WalletTransaction.builder()
                                .userId(userId)
                                .type("TOPUP")
                                .amount(amount)
                                .description(description)
                                .referenceId(referenceId)
                                .status("SUCCESS")
                                .build();
                transactionRepository.save(transaction);
        }

        @Transactional
        public void deductBalance(Long userId, Double amount, String description, String referenceId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                if (user.getBalance() < amount) {
                        throw new RuntimeException("Insufficient balance");
                }

                user.setBalance(user.getBalance() - amount);
                userRepository.save(user);

                WalletTransaction transaction = WalletTransaction.builder()
                                .userId(userId)
                                .type("PAYMENT")
                                .amount(amount)
                                .description(description)
                                .referenceId(referenceId)
                                .status("SUCCESS")
                                .build();
                transactionRepository.save(transaction);
        }

        @Transactional
        public void deductForWithdrawal(Long userId, Double amount, String description, String referenceId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                if (user.getBalance() < amount) {
                        throw new RuntimeException("Insufficient balance");
                }

                user.setBalance(user.getBalance() - amount);
                userRepository.save(user);

                WalletTransaction transaction = WalletTransaction.builder()
                                .userId(userId)
                                .type("WITHDRAWAL")
                                .amount(amount)
                                .description(description)
                                .referenceId(referenceId)
                                .status("PENDING") // Using PENDING for withdrawal
                                .build();
                transactionRepository.save(transaction);
        }

        @Transactional
        public void addPoints(Long userId, Long points, String description, String referenceId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                user.setPoints(user.getPoints() + points);
                userRepository.save(user);

                WalletTransaction transaction = WalletTransaction.builder()
                                .userId(userId)
                                .type("POINTS_EARNED")
                                .points(points)
                                .description(description)
                                .referenceId(referenceId)
                                .status("SUCCESS")
                                .build();
                transactionRepository.save(transaction);
        }

        @Transactional
        public void deductPoints(Long userId, Long points, Double discountAmount, String description,
                        String referenceId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                if (user.getPoints() < points) {
                        throw new RuntimeException("Insufficient points");
                }

                user.setPoints(user.getPoints() - points);
                userRepository.save(user);

                WalletTransaction transaction = WalletTransaction.builder()
                                .userId(userId)
                                .type("POINTS_REDEEMED")
                                .points(points)
                                .amount(discountAmount)
                                .description(description)
                                .referenceId(referenceId)
                                .status("SUCCESS")
                                .build();
                transactionRepository.save(transaction);
        }

        @Transactional
        public void refundBalance(Long userId, Double amount, String description, String referenceId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

                user.setBalance(user.getBalance() + amount);
                userRepository.save(user);

                WalletTransaction transaction = WalletTransaction.builder()
                                .userId(userId)
                                .type("REFUND")
                                .amount(amount)
                                .description(description)
                                .referenceId(referenceId)
                                .status("SUCCESS")
                                .build();
                transactionRepository.save(transaction);
        }

        private WalletTransactionDTO convertToDTO(WalletTransaction transaction) {
                return WalletTransactionDTO.builder()
                                .id(transaction.getId())
                                .type(transaction.getType())
                                .amount(transaction.getAmount())
                                .points(transaction.getPoints())
                                .description(transaction.getDescription())
                                .referenceId(transaction.getReferenceId())
                                .status(transaction.getStatus())
                                .createdAt(transaction.getCreatedAt())
                                .build();
        }
}
