package com.zelvira.zelvira.service;

import com.zelvira.zelvira.entity.Order;
import com.zelvira.zelvira.entity.OrderItem;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.OrderRepository;
import com.zelvira.zelvira.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderWorkflowService workflowService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final com.zelvira.zelvira.repository.ProductRepository productRepository;
    private final com.zelvira.zelvira.repository.ProductVariantRepository productVariantRepository;
    private final WalletService walletService;
    private final VoucherService voucherService;
    private final com.zelvira.zelvira.repository.AddressRepository addressRepository;
    private final com.zelvira.zelvira.repository.CategoryRepository categoryRepository;
    private final EmailService emailService;
    private final List<MarketplaceService> marketplaceServices;

    public OrderService(OrderRepository orderRepository,
            OrderWorkflowService workflowService,
            PaymentService paymentService,
            NotificationService notificationService,
            UserRepository userRepository,
            CartService cartService,
            com.zelvira.zelvira.repository.ProductRepository productRepository,
            com.zelvira.zelvira.repository.ProductVariantRepository productVariantRepository,
            WalletService walletService,
            VoucherService voucherService,
            com.zelvira.zelvira.repository.AddressRepository addressRepository,
            com.zelvira.zelvira.repository.CategoryRepository categoryRepository,
            EmailService emailService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) List<MarketplaceService> marketplaceServices) {
        this.orderRepository = orderRepository;
        this.workflowService = workflowService;
        this.paymentService = paymentService;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.walletService = walletService;
        this.voucherService = voucherService;
        this.addressRepository = addressRepository;
        this.categoryRepository = categoryRepository;
        this.emailService = emailService;
        this.marketplaceServices = marketplaceServices;
    }

    private void syncStockToMarketplaces(com.zelvira.zelvira.entity.Product product, Integer newStock) {
        if (marketplaceServices == null || product == null) return;
        for (MarketplaceService ms : marketplaceServices) {
            try {
                ms.updateStock(product, newStock);
            } catch (Exception e) {
                log.warn("Could not sync stock to marketplace {}: {}", ms.getMarketplaceType(), e.getMessage());
            }
        }
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public Order getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    public Order createOrder(Order order) {
        if (order.getOrderNumber() == null) {
            order.setOrderNumber("ORD-" + System.currentTimeMillis());
        }
        order.setStatus("PENDING");
        Order saved = orderRepository.save(order);
        paymentService.createTransaction(saved);
        saved = orderRepository.save(saved);

        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : "guest";
        workflowService.startOrderProcess(saved, username);

        User user = userRepository.findById(saved.getUserId()).orElse(null);
        if (user != null) {
            notificationService.notifyOrderPending(saved, user);
        }
        return saved;
    }

    public Order updateOrderStatus(Long id, String status) {
        Order order = getOrderById(id);
        String oldStatus = order.getStatus();
        order.setStatus(status);
        Order saved = orderRepository.save(order);

        if (!status.equals(oldStatus)) {
            User user = userRepository.findById(saved.getUserId()).orElse(null);
            if (user != null) {
                // Award points if order is paid (PROCESSING or PAID) and points not already
                // awarded
                if ((status.equals("PROCESSING") || status.equals("PAID")) &&
                        saved.getTotalPointsEarned() != null && saved.getTotalPointsEarned() > 0 &&
                        (saved.getPointsAwarded() == null || !saved.getPointsAwarded())) {

                    walletService.addPoints(user.getId(), saved.getTotalPointsEarned(),
                            "Points earned from order " + saved.getOrderNumber(), saved.getOrderNumber());

                    saved.setPointsAwarded(true);
                    orderRepository.save(saved);
                }

                // Send paid invoice email when transition to PAID / PROCESSING occurs
                if ((status.equalsIgnoreCase("PAID") || status.equalsIgnoreCase("PROCESSING"))
                        && !oldStatus.equalsIgnoreCase("PAID") && !oldStatus.equalsIgnoreCase("PROCESSING")) {
                    try {
                        if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                            emailService.sendPaidInvoiceEmail(user.getEmail(), saved);
                        }
                    } catch (Exception e) {
                        log.error("Failed to send paid invoice email for order {}: {}", saved.getOrderNumber(),
                                e.getMessage());
                    }
                }

                notificationService.notifyOrderStatusChanged(saved, user, status);
            }
        }
        return saved;
    }

    public Order updateOrderStatusByNumber(String orderNumber, String status) {
        Order order = getOrderByOrderNumber(orderNumber);
        return updateOrderStatus(order.getId(), status);
    }

    public Order updateTrackingNumber(Long id, String trackingNumber) {
        Order order = getOrderById(id);
        order.setTrackingNumber(trackingNumber);
        order.setStatus("DELIVERING");
        Order saved = orderRepository.save(order);

        User user = userRepository.findById(saved.getUserId()).orElse(null);
        if (user != null) {
            notificationService.notifyOrderStatusChanged(saved, user, "DELIVERING");
        }

        return saved;
    }

    @Transactional
    public com.zelvira.zelvira.dto.OrderResponse checkout(User user, com.zelvira.zelvira.dto.CheckoutRequest request) {
        // Ensure user is managed
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        double totalAmount = 0;
        List<OrderItem> orderItems = new java.util.ArrayList<>();

        if (request.getProductId() != null) {
            // BUY NOW FLOW
            com.zelvira.zelvira.entity.Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            com.zelvira.zelvira.entity.ProductVariant variant = productVariantRepository
                    .findById(request.getVariantId())
                    .orElseThrow(() -> new RuntimeException("Variant not found"));

            int quantity = request.getQuantity() != null ? request.getQuantity() : 1;
            if (quantity <= 0) {
                throw new IllegalArgumentException("Jumlah pesanan minimal 1");
            }

            int availableStock = variant.getStock() != null ? variant.getStock() : 0;
            if (availableStock <= 0) {
                throw new IllegalArgumentException("Stok untuk produk '" + product.getName() + "' habis");
            }
            if (quantity > availableStock) {
                throw new IllegalArgumentException(
                        "Pesanan melebihi stok yang tersedia (tersedia: " + availableStock + ")");
            }

            // Deduct stock
            variant.setStock(availableStock - quantity);
            productVariantRepository.save(variant);
            syncStockToMarketplaces(product, variant.getStock());

            double price = (variant.getDiscountPrice() != null ? variant.getDiscountPrice().doubleValue()
                    : variant.getPrice().doubleValue());
            totalAmount = price * quantity;

            orderItems.add(OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .groupName(variant.getGroupName())
                    .size(variant.getSize())
                    .color(variant.getColor())
                    .imageUrl(product.getImages() != null && !product.getImages().isEmpty()
                            ? product.getImages().get(0).getUrl()
                            : null)
                    .quantity(quantity)
                    .price(price)
                    .build());
        } else {
            // CART FLOW
            List<com.zelvira.zelvira.dto.CartItemResponse> cartItems = cartService.getCart(managedUser);
            if (cartItems == null || cartItems.isEmpty()) {
                throw new RuntimeException("Server error: Cart is empty for user " + user.getId());
            }

            // Validate all items before deducting stock
            for (com.zelvira.zelvira.dto.CartItemResponse cartItem : cartItems) {
                com.zelvira.zelvira.entity.ProductVariant variant = productVariantRepository
                        .findById(cartItem.getProductVariantId())
                        .orElseThrow(() -> new RuntimeException(
                                "Varian produk tidak ditemukan: " + cartItem.getProductName()));
                int availableStock = variant.getStock() != null ? variant.getStock() : 0;
                if (availableStock <= 0) {
                    throw new IllegalArgumentException("Stok produk '" + cartItem.getProductName() + " ("
                            + cartItem.getSize() + ", " + cartItem.getColor() + ")' habis");
                }
                if (cartItem.getQuantity() > availableStock) {
                    throw new IllegalArgumentException("Pesanan produk '" + cartItem.getProductName()
                            + "' melebihi stok yang tersedia (tersedia: " + availableStock + ")");
                }
            }

            // Deduct stock for all items
            for (com.zelvira.zelvira.dto.CartItemResponse cartItem : cartItems) {
                com.zelvira.zelvira.entity.ProductVariant variant = productVariantRepository
                        .findById(cartItem.getProductVariantId()).get();
                variant.setStock(variant.getStock() - cartItem.getQuantity());
                productVariantRepository.save(variant);
                if (variant.getProduct() != null) {
                    syncStockToMarketplaces(variant.getProduct(), variant.getStock());
                }
            }

            totalAmount = cartItems.stream()
                    .mapToDouble(item -> (item.getDiscountPrice() != null
                            ? item.getDiscountPrice().doubleValue()
                            : item.getPrice().doubleValue()) * item.getQuantity())
                    .sum();

            orderItems = cartItems.stream().map(cartItem -> OrderItem.builder()
                    .productId(cartItem.getProductId())
                    .productName(cartItem.getProductName())
                    .groupName(cartItem.getGroupName())
                    .size(cartItem.getSize())
                    .color(cartItem.getColor())
                    .imageUrl(cartItem.getImageUrl())
                    .quantity(cartItem.getQuantity())
                    .price(cartItem.getDiscountPrice() != null
                            ? cartItem.getDiscountPrice().doubleValue()
                            : cartItem.getPrice().doubleValue())
                    .build()).collect(Collectors.toList());
        }

        if (request.getShippingAmount() != null) {
            totalAmount += request.getShippingAmount();
        }

        // Handle voucher
        double voucherDiscount = 0;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isEmpty()) {
            try {
                com.zelvira.zelvira.entity.Voucher voucher = voucherService.getVoucherByCode(request.getVoucherCode());
                if (!voucher.getIsActive()) {
                    throw new RuntimeException("Voucher is inactive");
                }
                if (voucher.getValidUntil() != null && voucher.getValidUntil().isBefore(java.time.LocalDate.now())) {
                    throw new RuntimeException("Voucher expired");
                }
                if (voucher.getMinPurchase() != null && totalAmount < voucher.getMinPurchase()) {
                    throw new RuntimeException("Minimum purchase not met for voucher");
                }
                // Check tier
                long p = managedUser.getPoints() != null ? managedUser.getPoints() : 0L;
                String target = voucher.getTargetMembership();
                if (target != null && !target.equals("ALL")) {
                    if (target.equals("GOLD") && p < 100) {
                        throw new RuntimeException("Voucher is for GOLD members");
                    }
                    if (target.equals("PLATINUM") && p < 300) {
                        throw new RuntimeException("Voucher is for PLATINUM members");
                    }
                }

                if ("PERCENTAGE".equalsIgnoreCase(voucher.getDiscountType())) {
                    double discount = totalAmount * (voucher.getDiscountAmount() / 100.0);
                    if (voucher.getMaxDiscount() != null && discount > voucher.getMaxDiscount()) {
                        discount = voucher.getMaxDiscount();
                    }
                    voucherDiscount = discount;
                } else {
                    voucherDiscount = voucher.getDiscountAmount();
                }

                if (voucherDiscount > totalAmount) {
                    voucherDiscount = totalAmount;
                }
                totalAmount -= voucherDiscount;

            } catch (Exception e) {
                throw new RuntimeException("Invalid voucher: " + e.getMessage());
            }
        }

        // Calculate points earned (per category setting, falling back to product if
        // any)
        long totalPointsEarned = 0;
        for (OrderItem item : orderItems) {
            com.zelvira.zelvira.entity.Product product = productRepository.findById(item.getProductId()).orElse(null);
            if (product != null) {
                Long categoryPoints = null;
                if (product.getCategoryId() != null) {
                    com.zelvira.zelvira.entity.Category category = categoryRepository.findById(product.getCategoryId())
                            .orElse(null);
                    if (category != null && category.getPointsReward() != null && category.getPointsReward() > 0) {
                        categoryPoints = category.getPointsReward();
                    }
                }
                if (categoryPoints != null) {
                    totalPointsEarned += categoryPoints * item.getQuantity();
                } else if (product.getPointsEarned() != null && product.getPointsEarned() > 0) {
                    totalPointsEarned += product.getPointsEarned() * item.getQuantity();
                }
            }
        }

        // Handle points redemption: 1 Point = Rp 1 direct nominal discount
        double pointsDiscountAmount = 0;
        long actualPointsUsed = 0L;
        if (request.getPointsUsed() != null && request.getPointsUsed() > 0) {
            long userPoints = managedUser.getPoints() != null ? managedUser.getPoints() : 0L;
            if (userPoints < request.getPointsUsed()) {
                throw new IllegalArgumentException("Saldo poin tidak mencukupi (tersedia: " + userPoints
                        + ", digunakan: " + request.getPointsUsed() + ")");
            }
            // 1 Point = Rp 1 discount
            actualPointsUsed = request.getPointsUsed();
            if (actualPointsUsed > totalAmount) {
                actualPointsUsed = (long) Math.floor(totalAmount);
            }
            pointsDiscountAmount = (double) actualPointsUsed;
            totalAmount = Math.max(0, totalAmount - pointsDiscountAmount);

            if (actualPointsUsed > 0) {
                walletService.deductPoints(managedUser.getId(), actualPointsUsed, pointsDiscountAmount,
                        "Potongan belanja " + actualPointsUsed + " poin", null);
            }
        }

        // Handle wallet payment
        String status = "PENDING";
        double walletAmountUsed = 0;
        if ("WALLET".equalsIgnoreCase(request.getPaymentMethod())) {
            if (managedUser.getBalance() < totalAmount) {
                throw new RuntimeException("Insufficient wallet balance");
            }
            walletAmountUsed = totalAmount;
            walletService.deductBalance(managedUser.getId(), totalAmount, "Payment for order", null);
            status = "PROCESSING"; // Paid via wallet
        } else if (request.getWalletAmountUsed() != null && request.getWalletAmountUsed() > 0) {
            // Partial wallet payment
            if (managedUser.getBalance() < request.getWalletAmountUsed()) {
                throw new RuntimeException("Insufficient wallet balance");
            }
            walletAmountUsed = request.getWalletAmountUsed();
            walletService.deductBalance(managedUser.getId(), walletAmountUsed, "Partial payment for order", null);
            totalAmount -= walletAmountUsed;
        }

        String finalAddress = request.getShippingAddress();
        String finalRecipient = request.getShippingRecipient();
        String finalPhone = request.getShippingPhone();

        // Fallback to default user address if not provided in request
        if ((finalAddress == null || finalAddress.isEmpty()) && user.getId() != null) {
            java.util.List<com.zelvira.zelvira.entity.Address> addresses = addressRepository.findByUserId(user.getId());
            if (!addresses.isEmpty()) {
                com.zelvira.zelvira.entity.Address defaultAddr = addresses.stream()
                        .filter(a -> Boolean.TRUE.equals(a.getIsDefault()))
                        .findFirst()
                        .orElse(addresses.get(0));
                finalRecipient = defaultAddr.getRecipientName();
                finalPhone = defaultAddr.getPhoneNumber();
                finalAddress = String.format("%s, %s, %s, %s, %s %s",
                        defaultAddr.getAddressLine() != null ? defaultAddr.getAddressLine() : "",
                        defaultAddr.getSubdistrictName() != null ? defaultAddr.getSubdistrictName() : "",
                        defaultAddr.getDistrictName() != null ? defaultAddr.getDistrictName() : "",
                        defaultAddr.getCityName() != null ? defaultAddr.getCityName() : "",
                        defaultAddr.getProvinceName() != null ? defaultAddr.getProvinceName() : "",
                        defaultAddr.getPostalCode() != null ? defaultAddr.getPostalCode() : "")
                        .replaceAll("^[ ,]+|[ ,]+$", "");
            }
        }

        Order order = Order.builder()
                .userId(user.getId())
                .orderNumber("ORD-" + System.currentTimeMillis())
                .totalAmount(totalAmount)
                .shippingAmount(request.getShippingAmount())
                .shippingService(request.getShippingService())
                .destinationSubdistrictId(request.getDestinationSubdistrictId())
                .shippingAddress(finalAddress)
                .shippingRecipient(finalRecipient)
                .shippingPhone(finalPhone)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "MIDTRANS")
                .walletAmountUsed(walletAmountUsed)
                .pointsUsed(actualPointsUsed)
                .pointsDiscountAmount(pointsDiscountAmount)
                .totalPointsEarned(totalPointsEarned)
                .voucherDiscountAmount(voucherDiscount)
                .voucherCode(request.getVoucherCode())
                .status(status)
                .build();

        Order savedOrder = orderRepository.save(order);

        // Link order items to saved order
        for (OrderItem item : orderItems) {
            item.setOrder(savedOrder);
        }
        savedOrder.setItems(orderItems);
        orderRepository.save(savedOrder);

        if (totalAmount <= 0) {
            savedOrder.setStatus("PROCESSING");
            orderRepository.save(savedOrder);
        } else if (!status.equals("PROCESSING")) {
            paymentService.createTransaction(savedOrder);
            orderRepository.save(savedOrder);
        }
        workflowService.startOrderProcess(savedOrder, user.getEmail());

        if (request.getProductId() == null) {
            cartService.clearCart(user);
        }

        // Award points & notify user based on order status (Pending payment vs Instant
        // Wallet payment)
        if (savedOrder.getStatus().equalsIgnoreCase("PROCESSING") || savedOrder.getStatus().equalsIgnoreCase("PAID")) {
            if (savedOrder.getTotalPointsEarned() != null && savedOrder.getTotalPointsEarned() > 0 &&
                    (savedOrder.getPointsAwarded() == null || !savedOrder.getPointsAwarded())) {
                walletService.addPoints(managedUser.getId(), savedOrder.getTotalPointsEarned(),
                        "Poin didapatkan dari pesanan " + savedOrder.getOrderNumber(), savedOrder.getOrderNumber());
                savedOrder.setPointsAwarded(true);
                orderRepository.save(savedOrder);
            }
            notificationService.notifyPaymentSuccess(savedOrder, managedUser);
        } else {
            notificationService.notifyOrderPending(savedOrder, managedUser);
        }

        // Send order confirmation or paid invoice email
        try {
            if (managedUser.getEmail() != null && !managedUser.getEmail().trim().isEmpty()) {
                if (savedOrder.getStatus().equalsIgnoreCase("PROCESSING")
                        || savedOrder.getStatus().equalsIgnoreCase("PAID")) {
                    emailService.sendPaidInvoiceEmail(managedUser.getEmail(), savedOrder);
                } else {
                    String formattedAmount = String.format("Rp %,.0f", savedOrder.getTotalAmount()).replace(",", ".");
                    String customerName = managedUser.getFullName() != null
                            && !managedUser.getFullName().trim().isEmpty()
                                    ? managedUser.getFullName()
                                    : (managedUser.getEmail() != null ? managedUser.getEmail() : "Pelanggan");
                    emailService.sendOrderConfirmation(managedUser.getEmail(), savedOrder.getOrderNumber(),
                            customerName, formattedAmount);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send order email for order {}: {}", savedOrder.getOrderNumber(), e.getMessage());
        }

        return mapToResponse(savedOrder);
    }

    public com.zelvira.zelvira.dto.OrderResponse mapToResponse(Order order) {
        // Automatically generate payment token if it's missing for a pending order
        if (order.getStatus().equalsIgnoreCase("PENDING") &&
                (order.getPaymentToken() == null || order.getPaymentToken().isEmpty())) {
            try {
                paymentService.createTransaction(order);
                if (order.getPaymentToken() != null) {
                    orderRepository.save(order);
                }
            } catch (Exception e) {
                // Log and continue, client will see null token and show warning
            }
        }

        return com.zelvira.zelvira.dto.OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .trackingNumber(order.getTrackingNumber())
                .paymentToken(order.getPaymentToken())
                .paymentUrl(order.getPaymentUrl())
                .shippingAmount(order.getShippingAmount())
                .shippingService(order.getShippingService())
                .destinationSubdistrictId(order.getDestinationSubdistrictId())
                .shippingAddress(order.getShippingAddress())
                .shippingRecipient(order.getShippingRecipient())
                .shippingPhone(order.getShippingPhone())
                .paymentMethod(order.getPaymentMethod())
                .walletAmountUsed(order.getWalletAmountUsed())
                .pointsUsed(order.getPointsUsed())
                .pointsDiscountAmount(order.getPointsDiscountAmount())
                .totalPointsEarned(order.getTotalPointsEarned())
                .voucherCode(order.getVoucherCode())
                .voucherDiscountAmount(order.getVoucherDiscountAmount())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(order.getItems() != null ? order.getItems().stream()
                        .map(item -> com.zelvira.zelvira.dto.OrderItemResponse.builder()
                                .id(item.getId())
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .groupName(item.getGroupName())
                                .size(item.getSize())
                                .color(item.getColor())
                                .imageUrl(item.getImageUrl())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .isRated(item.getIsRated())
                                .build())
                        .collect(Collectors.toList()) : null)
                .build();
    }

    public String getOrGeneratePaymentToken(Long orderId, Long userId, boolean isAdminOrStaff) {
        Order order = getOrderById(orderId);
        if (!isAdminOrStaff && (order.getUserId() == null || !order.getUserId().equals(userId))) {
            throw new RuntimeException("Akses ditolak untuk pesanan ini");
        }
        if (!"PENDING".equalsIgnoreCase(order.getStatus())) {
            throw new RuntimeException("Pesanan sudah tidak dalam status menunggu pembayaran");
        }
        if (order.getPaymentToken() == null || order.getPaymentToken().isBlank()) {
            paymentService.createTransaction(order);
            if (order.getPaymentToken() != null) {
                orderRepository.save(order);
            }
        }
        return order.getPaymentToken();
    }

    public List<com.zelvira.zelvira.dto.OrderResponse> mapToResponseList(List<Order> orders) {
        return orders.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public void deleteOrder(Long id) {
        orderRepository.deleteById(id);
    }

    public Order syncPaymentStatus(Long id) {
        Order order = getOrderById(id);
        if ("PAID".equalsIgnoreCase(order.getStatus()) || "PROCESSING".equalsIgnoreCase(order.getStatus())
                || "DELIVERING".equalsIgnoreCase(order.getStatus())
                || "COMPLETED".equalsIgnoreCase(order.getStatus())) {
            return order;
        }

        try {
            org.json.JSONObject result = paymentService.checkTransaction(order.getOrderNumber());
            if (result != null) {
                String transactionStatus = result.optString("transaction_status", "");
                String fraudStatus = result.optString("fraud_status", "");

                log.info("Midtrans checkTransaction for order {}: status={}, fraud={}", 
                        order.getOrderNumber(), transactionStatus, fraudStatus);

                if ("settlement".equalsIgnoreCase(transactionStatus) ||
                        ("capture".equalsIgnoreCase(transactionStatus) && !"challenge".equalsIgnoreCase(fraudStatus))) {
                    return updateOrderStatus(order.getId(), "PAID");
                } else if ("deny".equalsIgnoreCase(transactionStatus) || "expire".equalsIgnoreCase(transactionStatus)
                        || "cancel".equalsIgnoreCase(transactionStatus)) {
                    return updateOrderStatus(order.getId(), "CANCELLED");
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync payment status for order: {}", order.getOrderNumber(), e);
        }
        return order;
    }

    public Order syncPaymentStatusByOrderNumber(String orderNumber) {
        Order order = getOrderByOrderNumber(orderNumber);
        return syncPaymentStatus(order.getId());
    }
}
