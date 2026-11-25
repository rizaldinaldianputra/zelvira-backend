package com.zelvira.zelvira.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zelvira.zelvira.dto.DigiflazzPriceListResponse;
import com.zelvira.zelvira.dto.DigiflazzRequest;
import com.zelvira.zelvira.dto.DigiflazzResponse;
import com.zelvira.zelvira.dto.DigiflazzTransactionResponse;
import com.zelvira.zelvira.entity.DigiflazzTransaction;
import com.zelvira.zelvira.repository.DigiflazzTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class DigiflazzService {

    private final DigiflazzTransactionRepository digiflazzTransactionRepository;
    private final WalletService walletService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.digiflazz.username}")
    private String username;

    @Value("${app.digiflazz.api-key}")
    private String apiKey;

    @Value("${app.digiflazz.base-url}")
    private String baseUrl;

    public DigiflazzService(DigiflazzTransactionRepository digiflazzTransactionRepository,
            WalletService walletService) {
        this.digiflazzTransactionRepository = digiflazzTransactionRepository;
        this.walletService = walletService;
    }

    public DigiflazzResponse<List<DigiflazzPriceListResponse>> getPriceList(String cmd) {
        String sign = generateSign("pricelist");

        DigiflazzRequest request = DigiflazzRequest.builder()
                .cmd(cmd != null ? cmd : "prepaid")
                .username(username)
                .sign(sign)
                .build();

        return sendRequest("/price-list", request,
                new ParameterizedTypeReference<DigiflazzResponse<List<DigiflazzPriceListResponse>>>() {
                });
    }

    public DigiflazzResponse<DigiflazzTransactionResponse> checkBalance() {
        String sign = generateSign("depo");

        DigiflazzRequest request = DigiflazzRequest.builder()
                .cmd("deposit")
                .username(username)
                .sign(sign)
                .build();

        return sendRequest("/cek-saldo", request,
                new ParameterizedTypeReference<DigiflazzResponse<DigiflazzTransactionResponse>>() {
                });
    }

    public DigiflazzResponse<DigiflazzTransactionResponse> topupPrepaid(Long userId, String buyerSkuCode,
            String customerNo, Double sellingPrice) {
        String refId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sign = generateSign(refId);

        DigiflazzTransaction transaction = DigiflazzTransaction.builder()
                .userId(userId)
                .refId(refId)
                .customerNo(customerNo)
                .buyerSkuCode(buyerSkuCode)
                .status("Pending")
                .transactionType("PREPAID")
                .sellingPrice(sellingPrice)
                .build();
        digiflazzTransactionRepository.save(transaction);

        DigiflazzRequest request = DigiflazzRequest.builder()
                .username(username)
                .buyerSkuCode(buyerSkuCode)
                .customerNo(customerNo)
                .refId(refId)
                .sign(sign)
                .testing(true)
                .build();

        DigiflazzResponse<DigiflazzTransactionResponse> response = sendRequest("/transaction", request,
                new ParameterizedTypeReference<DigiflazzResponse<DigiflazzTransactionResponse>>() {
                });

        if (response != null && response.getData() != null) {
            DigiflazzTransactionResponse data = response.getData();
            transaction.setRc(data.getRc());
            transaction.setStatus(data.getStatus());
            transaction.setSn(data.getSn());
            transaction.setMessage(data.getMessage());
            transaction.setPrice(data.getPrice());
            transaction.setBuyerLastSaldo(data.getBuyerLastSaldo());
            digiflazzTransactionRepository.save(transaction);
        }

        return response;
    }

    public DigiflazzResponse<DigiflazzTransactionResponse> inquiryPostpaid(Long userId, String buyerSkuCode,
            String customerNo) {
        String refId = "INQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String sign = generateSign(refId);

        DigiflazzRequest request = DigiflazzRequest.builder()
                .commands("inq-pasca")
                .username(username)
                .buyerSkuCode(buyerSkuCode)
                .customerNo(customerNo)
                .refId(refId)
                .sign(sign)
                .testing(true)
                .build();

        return sendRequest("/transaction", request,
                new ParameterizedTypeReference<DigiflazzResponse<DigiflazzTransactionResponse>>() {
                });
    }

    public DigiflazzResponse<DigiflazzTransactionResponse> payPostpaid(Long userId, String buyerSkuCode,
            String customerNo, String refId) {
        return payPostpaid(userId, buyerSkuCode, customerNo, refId, null);
    }

    public DigiflazzResponse<DigiflazzTransactionResponse> payPostpaid(Long userId, String buyerSkuCode,
            String customerNo, String refId, Double sellingPrice) {
        String sign = generateSign(refId);

        // Save transaction since it's a payment
        DigiflazzTransaction transaction = DigiflazzTransaction.builder()
                .userId(userId)
                .refId(refId)
                .customerNo(customerNo)
                .buyerSkuCode(buyerSkuCode)
                .status("Pending")
                .transactionType("POSTPAID")
                .sellingPrice(sellingPrice)
                .build();
        digiflazzTransactionRepository.save(transaction);

        DigiflazzRequest request = DigiflazzRequest.builder()
                .commands("pay-pasca")
                .username(username)
                .buyerSkuCode(buyerSkuCode)
                .customerNo(customerNo)
                .refId(refId)
                .sign(sign)
                .testing(true)
                .build();

        DigiflazzResponse<DigiflazzTransactionResponse> response = sendRequest("/transaction", request,
                new ParameterizedTypeReference<DigiflazzResponse<DigiflazzTransactionResponse>>() {
                });

        if (response != null && response.getData() != null) {
            DigiflazzTransactionResponse data = response.getData();
            transaction.setRc(data.getRc());
            transaction.setStatus(data.getStatus());
            transaction.setSn(data.getSn());
            transaction.setMessage(data.getMessage());
            transaction.setPrice(data.getPrice());
            transaction.setSellingPrice(data.getSellingPrice());
            transaction.setAdmin(data.getAdmin());
            digiflazzTransactionRepository.save(transaction);
        }

        return response;
    }

    public void processCallback(DigiflazzResponse<DigiflazzTransactionResponse> payload) {
        if (payload != null && payload.getData() != null) {
            DigiflazzTransactionResponse data = payload.getData();
            Optional<DigiflazzTransaction> optionalTx = digiflazzTransactionRepository.findByRefId(data.getRefId());
            if (optionalTx.isPresent()) {
                DigiflazzTransaction tx = optionalTx.get();
                tx.setStatus(data.getStatus());
                tx.setRc(data.getRc());
                tx.setSn(data.getSn());
                tx.setMessage(data.getMessage());
                if (data.getPrice() != null) {
                    tx.setPrice(data.getPrice());
                }
                digiflazzTransactionRepository.save(tx);
                log.info("Transaction updated from webhook: " + data.getRefId() + " status: " + data.getStatus());

                if ("Gagal".equalsIgnoreCase(data.getStatus())) {
                    Double refundAmount = tx.getSellingPrice() != null ? tx.getSellingPrice() : 0.0;
                    if (refundAmount > 0) {
                        walletService.refundBalance(tx.getUserId(), refundAmount,
                                "Refund Gagal Transaksi " + tx.getBuyerSkuCode(), tx.getRefId());
                        log.info("Triggered refund for: " + data.getRefId() + " amount: " + refundAmount);
                    }
                }
            } else {
                log.warn("Webhook received for unknown ref_id: " + data.getRefId());
            }
        }
    }

    private <T> DigiflazzResponse<T> sendRequest(String path, DigiflazzRequest request,
            ParameterizedTypeReference<DigiflazzResponse<T>> typeReference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<DigiflazzRequest> entity = new HttpEntity<>(request, headers);

        try {
            return restTemplate.exchange(baseUrl + path, HttpMethod.POST, entity, typeReference).getBody();
        } catch (Exception e) {
            log.error("Error calling Digiflazz API: ", e);
            throw new RuntimeException("Error communicating with Digiflazz API");
        }
    }

    private String generateSign(String identifier) {
        try {
            String input = username + apiKey + identifier;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xFF & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 Algorithm not found", e);
            throw new RuntimeException(e);
        }
    }
}
