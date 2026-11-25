package com.zelvira.zelvira.service;

import com.midtrans.Config;
import com.midtrans.ConfigFactory;
import com.midtrans.httpclient.error.MidtransError;
import com.midtrans.service.MidtransCoreApi;
import com.zelvira.zelvira.entity.WalletTransaction;
import com.zelvira.zelvira.repository.WalletTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TopupService {

    private static final Logger log = LoggerFactory.getLogger(TopupService.class);

    private final PaymentService paymentService;
    private final WalletService walletService;
    private final WalletTransactionRepository transactionRepository;
    private final Config midtransConfiguration;

    public TopupService(PaymentService paymentService,
            WalletService walletService,
            WalletTransactionRepository transactionRepository,
            Config midtransConfiguration) {
        this.paymentService = paymentService;
        this.walletService = walletService;
        this.transactionRepository = transactionRepository;
        this.midtransConfiguration = midtransConfiguration;
    }

    @Transactional
    public Map<String, Object> initiateTopup(Long userId, Double amount) {
        String transactionNumber = "TOPUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        JSONObject result = paymentService.createTopupTransaction(userId, amount, transactionNumber);

        WalletTransaction transaction = WalletTransaction.builder()
                .userId(userId)
                .type("TOPUP")
                .amount(amount)
                .description("Wallet Top-up via Midtrans")
                .referenceId(transactionNumber)
                .snapToken(result.optString("token", null))
                .status("PENDING")
                .build();
        WalletTransaction saved = transactionRepository.save(transaction);

        // Include transaction ID so client can call /verify endpoint after payment
        Map<String, Object> response = new java.util.HashMap<>(result.toMap());
        response.put("id", saved.getId());
        response.put("referenceId", transactionNumber);
        return response;
    }

    @Transactional
    public void processSuccessfulTopup(String transactionNumber) {
        WalletTransaction transaction = transactionRepository.findByReferenceId(transactionNumber);
        if (transaction != null && "PENDING".equals(transaction.getStatus())) {
            transaction.setStatus("SUCCESS");
            transaction.setDescription("Top-up Berhasil");
            transactionRepository.save(transaction);

            // Add balance directly to user without creating a second transaction record
            walletService.addBalanceOnly(transaction.getUserId(), transaction.getAmount());
            log.info("Topup {} marked as SUCCESS and balance added to user {}", transactionNumber,
                    transaction.getUserId());
        }
    }

    @Transactional
    public void processFailedTopup(String transactionNumber, String reason) {
        WalletTransaction transaction = transactionRepository.findByReferenceId(transactionNumber);
        if (transaction != null && "PENDING".equals(transaction.getStatus())) {
            String status = "expire".equalsIgnoreCase(reason) || "expired".equalsIgnoreCase(reason) ? "EXPIRED"
                    : "FAILED";
            transaction.setStatus(status);
            transaction.setDescription(status.equals("EXPIRED") ? "Top-up Kedaluwarsa" : "Top-up Dibatalkan / Gagal");
            transactionRepository.save(transaction);
            log.info("Topup {} marked as {}", transactionNumber, status);
        }
    }

    @Transactional
    public void processFailedTopup(String transactionNumber) {
        processFailedTopup(transactionNumber, "FAILED");
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumeTopup(Long transactionId, Long userId) {
        WalletTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaksi tidak ditemukan"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("Tidak memiliki akses");
        }

        if (!"PENDING".equals(transaction.getStatus())) {
            throw new RuntimeException("Transaksi sudah tidak pending");
        }

        if (transaction.getSnapToken() == null) {
            throw new RuntimeException("Token pembayaran tidak ditemukan. Silakan buat topup baru.");
        }

        return Map.of("token", transaction.getSnapToken());
    }

    /**
     * Verify topup status directly from Midtrans API and process if successful.
     * Used as fallback when webhook is not reachable (e.g. local dev environment).
     */
    @Transactional
    public Map<String, Object> verifyAndProcessTopup(Long transactionId, Long userId) {
        WalletTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaksi tidak ditemukan"));

        if (!transaction.getUserId().equals(userId)) {
            throw new RuntimeException("Tidak memiliki akses");
        }

        String referenceId = transaction.getReferenceId();

        // If already processed, return current state
        if ("SUCCESS".equals(transaction.getStatus())) {
            return Map.of("status", "SUCCESS", "message", "Topup sudah berhasil diproses");
        }
        if ("FAILED".equals(transaction.getStatus())) {
            return Map.of("status", "FAILED", "message", "Topup gagal");
        }

        // Query Midtrans directly for status
        try {
            MidtransCoreApi coreApi = new ConfigFactory(midtransConfiguration).getCoreApi();
            JSONObject result = coreApi.checkTransaction(referenceId);
            log.info("Midtrans status for {}: {}", referenceId, result.toString());

            String transactionStatus = result.optString("transaction_status", "");
            String fraudStatus = result.optString("fraud_status", "");

            boolean isSuccess = transactionStatus.equals("settlement") ||
                    (transactionStatus.equals("capture") && fraudStatus.equals("accept"));
            boolean isExpired = transactionStatus.equals("expire");
            boolean isFailed = transactionStatus.equals("deny") || transactionStatus.equals("cancel");

            if (isSuccess) {
                processSuccessfulTopup(referenceId);
                return Map.of("status", "SUCCESS", "message", "Top-up berhasil! Saldo telah ditambahkan.");
            } else if (isExpired) {
                processFailedTopup(referenceId, "expire");
                return Map.of("status", "EXPIRED", "message", "Waktu pembayaran telah habis (Kedaluwarsa).");
            } else if (isFailed) {
                processFailedTopup(referenceId, "failed");
                return Map.of("status", "FAILED", "message", "Pembayaran gagal atau dibatalkan.");
            } else {
                return Map.of("status", "PENDING", "message", "Pembayaran masih dalam proses: " + transactionStatus);
            }
        } catch (MidtransError e) {
            log.error("Midtrans API error checking topup {}: {}", referenceId, e.getMessage());
            throw new RuntimeException("Gagal mengecek status pembayaran: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error verifying topup {}", referenceId, e);
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }
}
