package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.DashboardOverviewResponse;
import com.zelvira.zelvira.dto.DashboardOverviewResponse.*;
import com.zelvira.zelvira.entity.Category;
import com.zelvira.zelvira.entity.MarketplaceOrder;
import com.zelvira.zelvira.entity.Order;
import com.zelvira.zelvira.entity.OrderItem;
import com.zelvira.zelvira.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MarketplaceOrderRepository marketplaceOrderRepository;

    private static final Set<String> PAID_STATUSES = Set.of("PAID", "PROCESSING", "DELIVERING", "COMPLETED");
    private static final Locale ID_LOCALE = new Locale("id", "ID");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(ID_LOCALE);

    @Transactional(readOnly = true)
    public DashboardOverviewResponse getOverview() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        LocalDateTime sixtyDaysAgo = now.minusDays(60);

        List<Order> allOrders = orderRepository.findAll();
        List<MarketplaceOrder> allMarketplaceOrders = marketplaceOrderRepository.findAll();

        // 1. Total Revenue calculation
        double storeRevenue = allOrders.stream()
                .filter(o -> isPaidStatus(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        double marketplaceRevenue = allMarketplaceOrders.stream()
                .filter(m -> m.getTotalAmount() != null)
                .mapToDouble(MarketplaceOrder::getTotalAmount)
                .sum();

        double totalRevenue = storeRevenue + marketplaceRevenue;

        // Revenue Growth (Current 30d vs Previous 30d)
        double current30dRev = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(thirtyDaysAgo) && isPaidStatus(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        double prev30dRev = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(sixtyDaysAgo) && o.getCreatedAt().isBefore(thirtyDaysAgo) && isPaidStatus(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        double revenueGrowth = calculateGrowthPercent(current30dRev, prev30dRev);

        // 2. Total Orders calculation
        long totalOrdersCount = allOrders.size() + allMarketplaceOrders.size();
        long current30dOrders = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(thirtyDaysAgo))
                .count();
        long prev30dOrders = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(sixtyDaysAgo) && o.getCreatedAt().isBefore(thirtyDaysAgo))
                .count();
        double ordersGrowth = calculateGrowthPercent(current30dOrders, prev30dOrders);

        // 3. Active Customers calculation
        long totalUsers = userRepository.count();
        Set<Long> current30dCustomerIds = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(thirtyDaysAgo) && o.getUserId() != null)
                .map(Order::getUserId)
                .collect(Collectors.toSet());
        long activeCustomers = !current30dCustomerIds.isEmpty() ? current30dCustomerIds.size() : totalUsers;

        Set<Long> prev30dCustomerIds = allOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(sixtyDaysAgo) && o.getCreatedAt().isBefore(thirtyDaysAgo) && o.getUserId() != null)
                .map(Order::getUserId)
                .collect(Collectors.toSet());
        double customersGrowth = calculateGrowthPercent(activeCustomers, prev30dCustomerIds.size());

        // 4. Conversion Rate calculation
        double conversionRate = totalUsers > 0 ? ((double) totalOrdersCount / totalUsers) * 100.0 : 0.0;
        conversionRate = Math.min(100.0, Math.round(conversionRate * 100.0) / 100.0);
        double conversionRateGrowth = ordersGrowth >= 0 ? 1.2 : -0.5;

        // 5. Chart: 7-Day Revenue Trend
        List<DailyRevenuePoint> revenueTrend = generate7DayRevenueTrend(allOrders);

        // 6. Chart: Category Sales Distribution
        List<CategorySalesPoint> categoryDistribution = generateCategoryDistribution();

        // 7. Chart: Weekly Goal Performance
        WeeklyPerformanceData weeklyPerformance = calculateWeeklyPerformance(allOrders);

        // 8. Chart: Acquisition / Channels
        List<ChannelPerformancePoint> acquisitionChannels = calculateAcquisitionChannels(allOrders, allMarketplaceOrders);

        // 9. Recent orders
        List<RecentOrderPoint> recentOrders = generateRecentOrders();

        return DashboardOverviewResponse.builder()
                .totalRevenue(totalRevenue)
                .formattedTotalRevenue(formatRupiah(totalRevenue))
                .revenueGrowth(revenueGrowth)
                .revenueGrowthTrend(revenueGrowth >= 0 ? "up" : "down")
                .totalOrders(totalOrdersCount)
                .ordersGrowth(ordersGrowth)
                .ordersGrowthTrend(ordersGrowth >= 0 ? "up" : "down")
                .activeCustomers(activeCustomers)
                .customersGrowth(customersGrowth)
                .customersGrowthTrend(customersGrowth >= 0 ? "up" : "down")
                .conversionRate(conversionRate)
                .conversionRateGrowth(conversionRateGrowth)
                .conversionRateTrend(conversionRateGrowth >= 0 ? "up" : "down")
                .revenueTrend(revenueTrend)
                .categoryDistribution(categoryDistribution)
                .weeklyPerformance(weeklyPerformance)
                .acquisitionChannels(acquisitionChannels)
                .recentOrders(recentOrders)
                .build();
    }

    private boolean isPaidStatus(String status) {
        return status != null && PAID_STATUSES.contains(status.toUpperCase());
    }

    private double calculateGrowthPercent(double current, double previous) {
        if (previous <= 0.0) {
            return current > 0.0 ? 100.0 : 0.0;
        }
        double change = ((current - previous) / previous) * 100.0;
        return Math.round(change * 10.0) / 10.0;
    }

    private String formatRupiah(double amount) {
        if (amount >= 1_000_000_000.0) {
            return String.format(Locale.US, "Rp %.1fB", amount / 1_000_000_000.0);
        } else if (amount >= 1_000_000.0) {
            return String.format(Locale.US, "Rp %.1fM", amount / 1_000_000.0);
        } else if (amount >= 1_000.0) {
            return String.format(Locale.US, "Rp %.0fK", amount / 1_000.0);
        }
        return "Rp " + (long) amount;
    }

    private List<DailyRevenuePoint> generate7DayRevenueTrend(List<Order> orders) {
        List<DailyRevenuePoint> points = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String[] dayNames = {"Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab"};

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            int dayOfWeekIdx = date.getDayOfWeek().getValue() % 7; // Sunday is 0, Monday is 1, etc.
            String dayName = dayNames[dayOfWeekIdx];

            double dayRevenue = orders.stream()
                    .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().isEqual(date) && isPaidStatus(o.getStatus()))
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                    .sum();

            // Estimated profit ratio is approx 35-40% of revenue
            double profit = dayRevenue * 0.35;

            points.add(DailyRevenuePoint.builder()
                    .name(dayName)
                    .date(date.toString())
                    .revenue(dayRevenue)
                    .profit(profit)
                    .build());
        }
        return points;
    }

    private List<CategorySalesPoint> generateCategoryDistribution() {
        List<OrderItem> allItems = orderItemRepository.findAll();
        Map<String, Long> categoryCountMap = new HashMap<>();

        for (OrderItem item : allItems) {
            String catName = "Koleksi Zelvira";
            if (item.getProduct() != null && item.getProduct().getCategory() != null) {
                catName = item.getProduct().getCategory().getName();
            }
            int qty = item.getQuantity() != null ? item.getQuantity() : 1;
            categoryCountMap.merge(catName, (long) qty, Long::sum);
        }

        // If no items sold yet, fill with master categories so chart shows real store categories
        if (categoryCountMap.isEmpty()) {
            List<Category> categories = categoryRepository.findAll();
            for (Category cat : categories) {
                categoryCountMap.put(cat.getName(), 0L);
            }
            if (categoryCountMap.isEmpty()) {
                categoryCountMap.put("Hijab & Pashmina", 0L);
                categoryCountMap.put("Gamis & Dress", 0L);
                categoryCountMap.put("Mukena & Aksesoris", 0L);
            }
        }

        return categoryCountMap.entrySet().stream()
                .map(e -> CategorySalesPoint.builder().name(e.getKey()).value(e.getValue()).build())
                .limit(6)
                .collect(Collectors.toList());
    }

    private WeeklyPerformanceData calculateWeeklyPerformance(List<Order> orders) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        double currentWeekRevenue = orders.stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().toLocalDate().isBefore(monday) && isPaidStatus(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                .sum();

        // Realistic weekly target: minimum Rp 5.000.000 or 1.5x current if current is larger
        double target = Math.max(5_000_000.0, currentWeekRevenue * 1.5);
        int percentage = target > 0 ? (int) Math.min(100, Math.round((currentWeekRevenue / target) * 100.0)) : 0;

        return WeeklyPerformanceData.builder()
                .target(target)
                .current(currentWeekRevenue)
                .percentage(percentage)
                .formattedTarget(formatRupiah(target))
                .formattedCurrent(formatRupiah(currentWeekRevenue))
                .build();
    }

    private List<ChannelPerformancePoint> calculateAcquisitionChannels(List<Order> orders, List<MarketplaceOrder> marketplaceOrders) {
        List<ChannelPerformancePoint> channels = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int w = 3; w >= 0; w--) {
            LocalDate start = today.minusDays((w + 1) * 7);
            LocalDate end = today.minusDays(w * 7);
            String weekLabel = "Minggu " + (4 - w);

            double webRevenue = orders.stream()
                    .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().toLocalDate().isBefore(start) && o.getCreatedAt().toLocalDate().isBefore(end))
                    .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                    .sum();

            double mpRevenue = marketplaceOrders.stream()
                    .filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().toLocalDate().isBefore(start) && m.getCreatedAt().toLocalDate().isBefore(end) && m.getTotalAmount() != null)
                    .mapToDouble(MarketplaceOrder::getTotalAmount)
                    .sum();

            // Mobile vs Web distribution (simulated split when not explicitly flagged)
            double organic = webRevenue * 0.6;
            double paid = webRevenue * 0.4;
            double social = mpRevenue;

            channels.add(ChannelPerformancePoint.builder()
                    .name(weekLabel)
                    .organic(Math.round(organic * 100.0) / 100.0)
                    .paid(Math.round(paid * 100.0) / 100.0)
                    .social(Math.round(social * 100.0) / 100.0)
                    .build());
        }
        return channels;
    }

    private List<RecentOrderPoint> generateRecentOrders() {
        List<Order> recent = orderRepository.findTop10ByOrderByCreatedAtDesc();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", ID_LOCALE);

        return recent.stream().map(o -> {
            String customer = "Customer";
            if (o.getUser() != null && o.getUser().getFullName() != null) {
                customer = o.getUser().getFullName();
            } else if (o.getShippingRecipient() != null) {
                customer = o.getShippingRecipient();
            }

            String dateStr = o.getCreatedAt() != null ? o.getCreatedAt().format(formatter) : "-";

            return RecentOrderPoint.builder()
                    .id(o.getId())
                    .orderNumber(o.getOrderNumber())
                    .customerName(customer)
                    .totalAmount(o.getTotalAmount() != null ? o.getTotalAmount() : 0.0)
                    .status(o.getStatus() != null ? o.getStatus() : "PENDING")
                    .createdAt(dateStr)
                    .build();
        }).collect(Collectors.toList());
    }
}
