package com.zelvira.zelvira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardOverviewResponse {

    // Metric Summary Cards
    private Double totalRevenue;
    private String formattedTotalRevenue;
    private Double revenueGrowth;
    private String revenueGrowthTrend; // "up" or "down"

    private Long totalOrders;
    private Double ordersGrowth;
    private String ordersGrowthTrend;

    private Long activeCustomers;
    private Double customersGrowth;
    private String customersGrowthTrend;

    private Double conversionRate;
    private Double conversionRateGrowth;
    private String conversionRateTrend;

    // Chart 1: Revenue trend over time (e.g. last 7 days)
    private List<DailyRevenuePoint> revenueTrend;

    // Chart 2: Category sales distribution
    private List<CategorySalesPoint> categoryDistribution;

    // Chart 3: Weekly performance vs Target
    private WeeklyPerformanceData weeklyPerformance;

    // Chart 4: Acquisition / Channel performance
    private List<ChannelPerformancePoint> acquisitionChannels;

    // Recent activity
    private List<RecentOrderPoint> recentOrders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyRevenuePoint {
        private String name; // e.g. "Sen", "Sel", "Rab"
        private String date; // YYYY-MM-DD
        private Double revenue;
        private Double profit;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategorySalesPoint {
        private String name;
        private Long value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WeeklyPerformanceData {
        private Double target;
        private Double current;
        private Integer percentage;
        private String formattedTarget;
        private String formattedCurrent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChannelPerformancePoint {
        private String name;
        private Double organic; // Web Storefront
        private Double paid;    // Mobile App
        private Double social;  // TikTok / Shopee Marketplace
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentOrderPoint {
        private Long id;
        private String orderNumber;
        private String customerName;
        private Double totalAmount;
        private String status;
        private String createdAt;
    }
}
