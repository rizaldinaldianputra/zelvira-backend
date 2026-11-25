package com.zelvira.zelvira.service;

import com.midtrans.Config;
import com.zelvira.zelvira.entity.Order;
import com.zelvira.zelvira.entity.OrderItem;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import com.midtrans.httpclient.error.MidtransError;
import com.midtrans.service.MidtransSnapApi;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PaymentService {

    private final Config midtransConfiguration;
    private final UserRepository userRepository;

    public PaymentService(Config midtransConfiguration, UserRepository userRepository) {
        this.midtransConfiguration = midtransConfiguration;
        this.userRepository = userRepository;
    }

    public void createTransaction(Order order) {
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            log.info("Order {} total amount is 0 or negative. Skipping Midtrans transaction.", order.getOrderNumber());
            return;
        }

        Map<String, Object> params = new HashMap<>();
        try {
            long grossAmount = order.getTotalAmount().longValue();

            Map<String, String> transactionDetails = new HashMap<>();
            transactionDetails.put("order_id", order.getOrderNumber());
            transactionDetails.put("gross_amount", String.valueOf(grossAmount));
            params.put("transaction_details", transactionDetails);

            List<Map<String, Object>> itemDetailsList = new ArrayList<>();
            if (order.getItems() != null) {
                for (com.zelvira.zelvira.entity.OrderItem item : order.getItems()) {
                    Map<String, Object> itemDetail = new HashMap<>();
                    itemDetail.put("id", String.valueOf(item.getProductId()));
                    itemDetail.put("price", item.getPrice().longValue());
                    itemDetail.put("quantity", item.getQuantity());
                    String pName = item.getProductName() != null ? item.getProductName() : "Product";
                    if (pName.length() > 45) pName = pName.substring(0, 45);
                    itemDetail.put("name", pName);
                    itemDetailsList.add(itemDetail);
                }
            }

            if (order.getShippingAmount() != null && order.getShippingAmount() > 0) {
                Map<String, Object> shippingDetail = new HashMap<>();
                shippingDetail.put("id", "SHIPPING");
                shippingDetail.put("price", order.getShippingAmount().longValue());
                shippingDetail.put("quantity", 1);
                String serviceName = order.getShippingService() != null ? order.getShippingService() : "Regular";
                if (serviceName.length() > 30) serviceName = serviceName.substring(0, 30);
                shippingDetail.put("name", "Shipping Cost (" + serviceName + ")");
                itemDetailsList.add(shippingDetail);
            }

            if (order.getVoucherDiscountAmount() != null && order.getVoucherDiscountAmount() > 0) {
                Map<String, Object> voucherDetail = new HashMap<>();
                voucherDetail.put("id", "VOUCHER");
                voucherDetail.put("price", -order.getVoucherDiscountAmount().longValue());
                voucherDetail.put("quantity", 1);
                voucherDetail.put("name", "Diskon Voucher");
                itemDetailsList.add(voucherDetail);
            }

            if (order.getPointsDiscountAmount() != null && order.getPointsDiscountAmount() > 0) {
                Map<String, Object> pointsDetail = new HashMap<>();
                pointsDetail.put("id", "POINTS");
                pointsDetail.put("price", -order.getPointsDiscountAmount().longValue());
                pointsDetail.put("quantity", 1);
                pointsDetail.put("name", "Potongan Poin");
                itemDetailsList.add(pointsDetail);
            }

            if (order.getWalletAmountUsed() != null && order.getWalletAmountUsed() > 0) {
                Map<String, Object> walletDetail = new HashMap<>();
                walletDetail.put("id", "WALLET");
                walletDetail.put("price", -order.getWalletAmountUsed().longValue());
                walletDetail.put("quantity", 1);
                walletDetail.put("name", "Saldo Dompet");
                itemDetailsList.add(walletDetail);
            }

            long sumItems = 0;
            for (Map<String, Object> it : itemDetailsList) {
                long price = ((Number) it.get("price")).longValue();
                int qty = ((Number) it.get("quantity")).intValue();
                sumItems += (price * qty);
            }

            if (sumItems == grossAmount) {
                params.put("item_details", itemDetailsList);
            } else {
                log.warn("Item details sum ({}) != gross amount ({}). Omitting item_details for order {} to prevent Midtrans validation error.",
                        sumItems, grossAmount, order.getOrderNumber());
                params.remove("item_details");
            }

            com.zelvira.zelvira.entity.User user = userRepository.findById(order.getUserId()).orElse(null);
            if (user != null) {
                Map<String, String> customerDetails = new HashMap<>();
                customerDetails.put("first_name", user.getFullName() != null ? user.getFullName() : "Customer");
                customerDetails.put("email", user.getEmail());
                if (order.getShippingPhone() != null && !order.getShippingPhone().isBlank()) {
                    customerDetails.put("phone", order.getShippingPhone());
                }
                params.put("customer_details", customerDetails);
            }

            Map<String, Object> expiry = new HashMap<>();
            expiry.put("start_time", java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
                    .format(java.time.ZonedDateTime.now().plusMinutes(1)));
            expiry.put("duration", 24);
            expiry.put("unit", "hours");
            params.put("expiry", expiry);

            String frontendUrl = System.getenv("FRONTEND_URL");
            if (frontendUrl == null)
                frontendUrl = "http://localhost:3000";
            if (frontendUrl.endsWith("/"))
                frontendUrl = frontendUrl.substring(0, frontendUrl.length() - 1);

            Map<String, String> callbacks = new HashMap<>();
            callbacks.put("finish", frontendUrl + "/payment/finish");
            callbacks.put("unfinish", frontendUrl + "/payment/unfinish");
            callbacks.put("error", frontendUrl + "/payment/error");
            params.put("callbacks", callbacks);

            log.info("Creating Midtrans transaction for order: {} with gross_amount: {}", order.getOrderNumber(), grossAmount);
            com.midtrans.service.MidtransSnapApi snapApi = new com.midtrans.ConfigFactory(midtransConfiguration)
                    .getSnapApi();
            JSONObject result = snapApi.createTransaction(params);

            log.info("Midtrans transaction result for order {}: {}", order.getOrderNumber(), result.toString());

            if (result.has("token")) {
                order.setPaymentToken(result.getString("token"));
            }
            if (result.has("redirect_url")) {
                order.setPaymentUrl(result.getString("redirect_url"));
            }

        } catch (MidtransError e) {
            log.error("Failed to generate Midtrans transaction for order {}: {}", order.getOrderNumber(), e.getMessage());
            // Retry strategy 1: remove item_details if present and retry
            if (params.containsKey("item_details")) {
                try {
                    log.info("Retrying Midtrans transaction without item_details for order {}", order.getOrderNumber());
                    params.remove("item_details");
                    com.midtrans.service.MidtransSnapApi snapApi = new com.midtrans.ConfigFactory(midtransConfiguration)
                            .getSnapApi();
                    JSONObject result = snapApi.createTransaction(params);
                    if (result.has("token")) {
                        order.setPaymentToken(result.getString("token"));
                    }
                    if (result.has("redirect_url")) {
                        order.setPaymentUrl(result.getString("redirect_url"));
                    }
                    return;
                } catch (Exception retryEx) {
                    log.error("Retry without item_details failed for order {}", order.getOrderNumber(), retryEx);
                }
            }
            // Retry strategy 2: if order_id has already been taken, retry with timestamp suffix
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("order_id")) {
                try {
                    String retryOrderId = order.getOrderNumber() + "-" + (System.currentTimeMillis() % 100000);
                    log.info("Retrying Midtrans with updated order_id: {}", retryOrderId);
                    @SuppressWarnings("unchecked")
                    Map<String, String> td = (Map<String, String>) params.get("transaction_details");
                    if (td != null) {
                        td.put("order_id", retryOrderId);
                    }
                    params.remove("item_details");
                    com.midtrans.service.MidtransSnapApi snapApi = new com.midtrans.ConfigFactory(midtransConfiguration)
                            .getSnapApi();
                    JSONObject result = snapApi.createTransaction(params);
                    if (result.has("token")) {
                        order.setPaymentToken(result.getString("token"));
                    }
                    if (result.has("redirect_url")) {
                        order.setPaymentUrl(result.getString("redirect_url"));
                    }
                } catch (Exception retryEx2) {
                    log.error("Retry with updated order_id failed for order {}", order.getOrderNumber(), retryEx2);
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error generating Midtrans transaction for order {}", order.getOrderNumber(), e);
        }
    }

    public JSONObject createTopupTransaction(Long userId, Double amount, String transactionNumber) {
        try {
            Map<String, Object> params = new HashMap<>();

            Map<String, String> transactionDetails = new HashMap<>();
            transactionDetails.put("order_id", transactionNumber);
            transactionDetails.put("gross_amount", String.valueOf(amount.intValue()));
            params.put("transaction_details", transactionDetails);

            List<Map<String, Object>> itemDetailsList = new ArrayList<>();
            Map<String, Object> itemDetail = new HashMap<>();
            itemDetail.put("id", "TOPUP");
            itemDetail.put("price", amount.longValue());
            itemDetail.put("quantity", 1);
            itemDetail.put("name", "Wallet Top-up");
            itemDetailsList.add(itemDetail);
            params.put("item_details", itemDetailsList);

            com.zelvira.zelvira.entity.User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                Map<String, String> customerDetails = new HashMap<>();
                customerDetails.put("first_name", user.getFullName() != null ? user.getFullName() : "Customer");
                customerDetails.put("email", user.getEmail());
                params.put("customer_details", customerDetails);
            }

            String frontendUrl = System.getenv("FRONTEND_URL");
            if (frontendUrl == null)
                frontendUrl = "http://localhost:3000";
            if (frontendUrl.endsWith("/"))
                frontendUrl = frontendUrl.substring(0, frontendUrl.length() - 1);

            Map<String, String> callbacks = new HashMap<>();
            callbacks.put("finish", frontendUrl + "/profile/wallet");
            params.put("callbacks", callbacks);

            log.info("Creating Midtrans topup for user: {} with params: {}", userId, params);
            com.midtrans.service.MidtransSnapApi snapApi = new com.midtrans.ConfigFactory(midtransConfiguration)
                    .getSnapApi();
            return snapApi.createTransaction(params);

        } catch (MidtransError e) {
            log.error("Failed to generate Midtrans topup for user {}", userId, e);
            throw new RuntimeException("Midtrans Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error generating Midtrans topup", e);
            throw new RuntimeException("Unexpected Error: " + e.getMessage());
        }
    }

    public void createDigitalOrderTransaction(com.zelvira.zelvira.entity.DigitalOrder order, User user) {
        try {
            Map<String, Object> params = new HashMap<>();

            Map<String, String> transactionDetails = new HashMap<>();
            transactionDetails.put("order_id", order.getOrderNumber());
            transactionDetails.put("gross_amount", String.valueOf(order.getPrice().intValue()));
            params.put("transaction_details", transactionDetails);

            List<Map<String, Object>> itemDetailsList = new ArrayList<>();
            Map<String, Object> itemDetail = new HashMap<>();
            itemDetail.put("id", order.getSkuCode());
            itemDetail.put("price", order.getPrice().longValue());
            itemDetail.put("quantity", 1);
            itemDetail.put("name", order.getProductName().length() > 50 ? order.getProductName().substring(0, 50)
                    : order.getProductName());
            itemDetailsList.add(itemDetail);
            params.put("item_details", itemDetailsList);

            Map<String, String> customerDetails = new HashMap<>();
            String customerName = user != null && user.getFullName() != null ? user.getFullName()
                    : "Pelanggan " + order.getCustomerNo();
            String email = order.getCustomerEmail() != null && !order.getCustomerEmail().isEmpty()
                    ? order.getCustomerEmail()
                    : (user != null && user.getEmail() != null ? user.getEmail() : "customer@zelvira.com");
            customerDetails.put("first_name", customerName);
            customerDetails.put("email", email);
            customerDetails.put("phone", order.getCustomerNo());
            params.put("customer_details", customerDetails);

            log.info("Creating Midtrans transaction for digital order: {} with params: {}", order.getOrderNumber(),
                    params);
            com.midtrans.service.MidtransSnapApi snapApi = new com.midtrans.ConfigFactory(midtransConfiguration)
                    .getSnapApi();
            JSONObject result = snapApi.createTransaction(params);

            log.info("Midtrans transaction result for digital order {}: {}", order.getOrderNumber(), result.toString());

            if (result.has("token")) {
                order.setPaymentToken(result.getString("token"));
            }
            if (result.has("redirect_url")) {
                order.setPaymentUrl(result.getString("redirect_url"));
            }
        } catch (MidtransError e) {
            log.error("Failed to generate Midtrans transaction for digital order {}", order.getOrderNumber(), e);
            throw new RuntimeException("Gagal menghubungkan ke Midtrans: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error generating Midtrans digital order transaction", e);
            throw new RuntimeException("Gagal membuat pembayaran Midtrans: " + e.getMessage());
        }
    }

    public JSONObject checkTransaction(String orderId) {
        try {
            com.midtrans.service.MidtransCoreApi coreApi = new com.midtrans.ConfigFactory(midtransConfiguration)
                    .getCoreApi();
            return coreApi.checkTransaction(orderId);
        } catch (Exception e) {
            log.error("Failed to check Midtrans status for orderId: {}", orderId, e);
            return null;
        }
    }
}
