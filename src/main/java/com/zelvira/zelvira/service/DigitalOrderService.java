package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.DigiflazzResponse;
import com.zelvira.zelvira.dto.DigiflazzTransactionResponse;
import com.zelvira.zelvira.dto.DigitalOrderRequest;
import com.zelvira.zelvira.entity.DigitalOrder;
import com.zelvira.zelvira.entity.DigitalProduct;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.DigitalOrderRepository;
import com.zelvira.zelvira.repository.DigitalProductRepository;
import com.zelvira.zelvira.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@Slf4j
public class DigitalOrderService {

    private final DigitalProductRepository digitalProductRepository;
    private final DigitalOrderRepository digitalOrderRepository;
    private final DigiflazzService digiflazzService;
    private final WalletService walletService;
    private final PaymentService paymentService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    public DigitalOrderService(DigitalProductRepository digitalProductRepository,
            DigitalOrderRepository digitalOrderRepository, DigiflazzService digiflazzService,
            WalletService walletService, PaymentService paymentService, EmailService emailService,
            UserRepository userRepository) {
        this.digitalProductRepository = digitalProductRepository;
        this.digitalOrderRepository = digitalOrderRepository;
        this.digiflazzService = digiflazzService;
        this.walletService = walletService;
        this.paymentService = paymentService;
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    /**
     * Create digital order with Midtrans Snap token
     */
    public DigitalOrder createDigitalOrder(Long userId, String defaultEmail, DigitalOrderRequest request) {
        DigitalProduct product = digitalProductRepository.findByBuyerSkuCode(request.getSkuCode())
                .orElseThrow(() -> new RuntimeException("Produk digital tidak ditemukan: " + request.getSkuCode()));

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        String email = request.getEmail() != null && !request.getEmail().trim().isEmpty()
                ? request.getEmail().trim()
                : (defaultEmail != null && !defaultEmail.trim().isEmpty() ? defaultEmail
                        : (user != null ? user.getEmail() : "customer@zelvira.com"));

        Double price = product.getSellingPrice() != null && product.getSellingPrice() > 0
                ? product.getSellingPrice()
                : product.getPrice();

        String orderNumber = "DIGI-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 4).toUpperCase();

        DigitalOrder order = DigitalOrder.builder()
                .orderNumber(orderNumber)
                .userId(userId)
                .customerEmail(email)
                .customerNo(request.getCustomerNo())
                .skuCode(product.getBuyerSkuCode())
                .productName(product.getProductName())
                .category(product.getCategory())
                .brand(product.getBrand())
                .price(price)
                .paymentStatus("PENDING")
                .digiflazzStatus("PENDING")
                .build();

        // Generate Midtrans Snap transaction
        paymentService.createDigitalOrderTransaction(order, user);

        return digitalOrderRepository.save(order);
    }

    /**
     * Handle Midtrans Payment Settlement -> Trigger Digiflazz -> Send Invoice Email
     */
    @Transactional
    public void handlePaymentSuccess(String orderNumber) {
        log.info("Handling payment success for digital order: {}", orderNumber);
        DigitalOrder order = digitalOrderRepository.findByOrderNumber(orderNumber)
                .orElse(null);

        if (order == null) {
            log.warn("Digital order not found for order number: {}", orderNumber);
            return;
        }

        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            log.info("Digital order {} is already marked as PAID. Skipping duplicate processing.", orderNumber);
            return;
        }

        // 1. Mark as PAID
        order.setPaymentStatus("PAID");

        // 2. Execute to Digiflazz
        try {
            log.info("Executing Digiflazz topup for order {}: SKU={}, CustomerNo={}",
                    order.getOrderNumber(), order.getSkuCode(), order.getCustomerNo());

            DigiflazzResponse<DigiflazzTransactionResponse> digiResponse = digiflazzService.topupPrepaid(
                    order.getUserId(),
                    order.getSkuCode(),
                    order.getCustomerNo(),
                    order.getPrice());

            if (digiResponse != null && digiResponse.getData() != null) {
                DigiflazzTransactionResponse data = digiResponse.getData();
                order.setDigiflazzStatus(data.getStatus() != null ? data.getStatus() : "Processing");
                order.setDigiflazzSn(data.getSn());
                order.setDigiflazzMessage(data.getMessage());
                log.info("Digiflazz execution result for {}: status={}, SN={}",
                        orderNumber, data.getStatus(), data.getSn());
            } else {
                order.setDigiflazzStatus("Processing");
                order.setDigiflazzMessage("Pesanan sedang diproses provider");
            }
        } catch (Exception e) {
            log.error("Failed to execute Digiflazz topup for digital order {}", orderNumber, e);
            order.setDigiflazzStatus("FAILED");
            order.setDigiflazzMessage("Gagal eksekusi ke provider: " + e.getMessage());
        }

        digitalOrderRepository.save(order);

        // 3. Send invoice to buyer's email
        try {
            if (order.getCustomerEmail() != null && !order.getCustomerEmail().isEmpty()) {
                log.info("Sending digital order invoice email to {}", order.getCustomerEmail());
                emailService.sendDigitalInvoiceEmail(order.getCustomerEmail(), order);
            }
        } catch (Exception e) {
            log.error("Failed to send digital invoice email for order {}", orderNumber, e);
        }
    }

    /**
     * Handle payment failure / expiration
     */
    @Transactional
    public void handlePaymentFailed(String orderNumber) {
        log.info("Handling payment failure for digital order: {}", orderNumber);
        digitalOrderRepository.findByOrderNumber(orderNumber).ifPresent(order -> {
            order.setPaymentStatus("FAILED");
            digitalOrderRepository.save(order);
        });
    }

    public DigitalOrder getOrderByNumber(String orderNumber) {
        return digitalOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Pesanan digital tidak ditemukan: " + orderNumber));
    }

    public List<DigitalOrder> getMyOrders(Long userId) {
        return digitalOrderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public DigiflazzResponse<DigiflazzTransactionResponse> processPrepaidOrder(Long userId,
            DigitalOrderRequest request) {
        // 1. Get Product
        DigitalProduct product = digitalProductRepository.findByBuyerSkuCode(request.getSkuCode())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getIsPostpaid()) {
            throw new RuntimeException("This endpoint is for prepaid products only");
        }

        // 2. Check & Deduct Wallet
        Double priceToPay = product.getSellingPrice();
        String refDesc = "Topup " + product.getProductName() + " - " + request.getCustomerNo();

        // We temporarily use UUID for our own internal wallet reference,
        // though digiflazz generates its own ref_id in DigiflazzService
        String internalRef = "PREPAID-" + System.currentTimeMillis();

        walletService.deductBalance(userId, priceToPay, refDesc, internalRef);

        // 3. Hit Digiflazz
        try {
            DigiflazzResponse<DigiflazzTransactionResponse> response = digiflazzService.topupPrepaid(userId,
                    request.getSkuCode(), request.getCustomerNo(), priceToPay);

            // If immediately Gagal, refund
            if (response != null && response.getData() != null) {
                if ("Gagal".equalsIgnoreCase(response.getData().getStatus())) {
                    walletService.refundBalance(userId, priceToPay, "Refund Gagal: " + refDesc,
                            response.getData().getRefId());
                }
            }

            return response;
        } catch (Exception e) {
            // Refund if internal error hitting digiflazz API
            walletService.refundBalance(userId, priceToPay, "Refund Error: " + refDesc, internalRef);
            throw new RuntimeException("Failed to process transaction: " + e.getMessage());
        }
    }

    // Postpaid Inquiry
    public DigiflazzResponse<DigiflazzTransactionResponse> processPostpaidInquiry(Long userId,
            DigitalOrderRequest request) {
        // Just bypass directly to digiflazz service for inquiry
        // DigiflazzService needs an inquiry method.
        // For simplicity, reusing topupPrepaid method logic but passing "inq-pasca"
        // commands.
        // Wait, DigiflazzService doesn't have it yet.
        // Let's call a new method in DigiflazzService: inquiryPostpaid.
        return digiflazzService.inquiryPostpaid(userId, request.getSkuCode(), request.getCustomerNo());
    }

    // Postpaid Pay
    public DigiflazzResponse<DigiflazzTransactionResponse> processPostpaidPay(Long userId, DigitalOrderRequest request,
            Double amountToDeduct, String refId) {
        // amountToDeduct comes from what user confirmed from the inquiry response
        walletService.deductBalance(userId, amountToDeduct, "Payment Postpaid " + request.getCustomerNo(), refId);

        try {
            DigiflazzResponse<DigiflazzTransactionResponse> response = digiflazzService.payPostpaid(userId,
                    request.getSkuCode(), request.getCustomerNo(), refId, amountToDeduct);

            if (response != null && response.getData() != null) {
                if ("Gagal".equalsIgnoreCase(response.getData().getStatus())) {
                    walletService.refundBalance(userId, amountToDeduct, "Refund Gagal Postpaid", refId);
                }
            }
            return response;
        } catch (Exception e) {
            walletService.refundBalance(userId, amountToDeduct, "Refund Error Postpaid", refId);
            throw new RuntimeException("Failed to process transaction");
        }
    }
}
