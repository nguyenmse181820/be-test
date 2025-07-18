package com.boeing.bookingservice.service;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.entity.Payment;
import com.boeing.bookingservice.model.enums.BookingStatus;
import com.boeing.bookingservice.model.enums.PaymentStatus;
import com.boeing.bookingservice.repository.BookingDetailRepository;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingDetailRepository bookingDetailRepository;

    public Map<String, Object> getDashboardStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        // Basic counts
        long totalBookings = bookingRepository.count();
        long totalPayments = paymentRepository.count();
        long totalBookingDetails = bookingDetailRepository.count();

        // Today's statistics
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        List<Booking> todaysBookings = bookingRepository.findByBookingDateBetween(startOfDay.toLocalDate(), endOfDay.toLocalDate());
        long todaysBookingCount = todaysBookings.size();
        double todaysRevenue = todaysBookings.stream()
                .mapToDouble(booking -> booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                .sum();

        // This month's statistics
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDateTime startOfMonthDateTime = startOfMonth.atStartOfDay();

        List<Booking> monthlyBookings = bookingRepository.findByBookingDateBetween(startOfMonthDateTime.toLocalDate(), endOfDay.toLocalDate());
        long monthlyBookingCount = monthlyBookings.size();
        double monthlyRevenue = monthlyBookings.stream()
                .mapToDouble(booking -> booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                .sum();

        // Booking status distribution
        Map<BookingStatus, Long> bookingStatusCounts = bookingRepository.findAll().stream()
                .collect(Collectors.groupingBy(Booking::getStatus, Collectors.counting()));

        // Payment statistics
        long successfulPayments = paymentRepository.countByStatus(PaymentStatus.COMPLETED);
        long failedPayments = paymentRepository.countByStatus(PaymentStatus.FAILED);
        double paymentSuccessRate = totalPayments > 0 ? (double) successfulPayments / totalPayments * 100 : 0;

        statistics.put("totalBookings", totalBookings);
        statistics.put("totalPayments", totalPayments);
        statistics.put("totalBookingDetails", totalBookingDetails);
        statistics.put("todaysBookings", todaysBookingCount);
        statistics.put("todaysRevenue", todaysRevenue);
        statistics.put("monthlyBookings", monthlyBookingCount);
        statistics.put("monthlyRevenue", monthlyRevenue);
        statistics.put("bookingStatusDistribution", bookingStatusCounts);
        statistics.put("paymentSuccessRate", paymentSuccessRate);
        statistics.put("successfulPayments", successfulPayments);
        statistics.put("failedPayments", failedPayments);

        return statistics;
    }

    public Map<String, Object> getBookingAnalytics(int days) {
        Map<String, Object> analytics = new HashMap<>();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Booking> bookings = bookingRepository.findByBookingDateBetween(startDateTime.toLocalDate(), endDateTime.toLocalDate());

        // Daily booking counts
        Map<LocalDate, Long> dailyBookingCounts = bookings.stream()
                .collect(Collectors.groupingBy(
                        Booking::getBookingDate,
                        Collectors.counting()
                ));

        // Average booking value
        double averageBookingValue = bookings.stream()
                .mapToDouble(booking -> booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                .average()
                .orElse(0.0);

        // Popular routes
        List<BookingDetail> bookingDetails = bookingDetailRepository.findByBookingIn(bookings);
        Map<String, Long> popularRoutes = bookingDetails.stream()
                .collect(Collectors.groupingBy(
                        detail -> detail.getOriginAirportCode() + " → " + detail.getDestinationAirportCode(),
                        Collectors.counting()
                ));

        analytics.put("dailyBookingCounts", dailyBookingCounts);
        analytics.put("averageBookingValue", averageBookingValue);
        analytics.put("popularRoutes", popularRoutes);
        analytics.put("totalBookingsInPeriod", bookings.size());

        return analytics;
    }

    public Map<String, Object> getRevenueTrends(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> trends = new HashMap<>();

        if (startDate == null) {
            startDate = LocalDate.now().minusDays(30);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Booking> bookings = bookingRepository.findByBookingDateBetween(startDateTime.toLocalDate(), endDateTime.toLocalDate());

        // Daily revenue
        Map<LocalDate, Double> dailyRevenue = bookings.stream()
                .collect(Collectors.groupingBy(
                        Booking::getBookingDate,
                        Collectors.summingDouble(booking -> booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                ));

        // Total revenue
        double totalRevenue = bookings.stream()
                .mapToDouble(booking -> booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                .sum();

        // Revenue growth (compare with previous period)
        LocalDate previousStartDate = startDate.minusDays(ChronoUnit.DAYS.between(startDate, endDate) + 1);
        LocalDateTime previousStartDateTime = previousStartDate.atStartOfDay();
        LocalDateTime previousEndDateTime = startDate.atStartOfDay();

        List<Booking> previousBookings = bookingRepository.findByBookingDateBetween(previousStartDateTime.toLocalDate(), previousEndDateTime.toLocalDate());
        double previousRevenue = previousBookings.stream()
                .mapToDouble(booking -> booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                .sum();

        double revenueGrowth = previousRevenue > 0 ? ((totalRevenue - previousRevenue) / previousRevenue) * 100 : 0;

        trends.put("dailyRevenue", dailyRevenue);
        trends.put("totalRevenue", totalRevenue);
        trends.put("previousRevenue", previousRevenue);
        trends.put("revenueGrowth", revenueGrowth);

        return trends;
    }

    public Map<String, Object> getCustomerAnalytics() {
        Map<String, Object> analytics = new HashMap<>();

        List<Booking> allBookings = bookingRepository.findAll();

        // Unique customers
        Set<UUID> uniqueCustomers = allBookings.stream()
                .map(Booking::getUserId)
                .collect(Collectors.toSet());

        // Customer booking frequency
        Map<UUID, Long> customerBookingCounts = allBookings.stream()
                .collect(Collectors.groupingBy(Booking::getUserId, Collectors.counting()));

        // Average bookings per customer
        double averageBookingsPerCustomer = customerBookingCounts.values().stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        // Repeat customers (more than 1 booking)
        long repeatCustomers = customerBookingCounts.values().stream()
                .filter(count -> count > 1)
                .count();

        analytics.put("uniqueCustomers", uniqueCustomers.size());
        analytics.put("averageBookingsPerCustomer", averageBookingsPerCustomer);
        analytics.put("repeatCustomers", repeatCustomers);
        analytics.put("customerRetentionRate", !uniqueCustomers.isEmpty() ? (double) repeatCustomers / uniqueCustomers.size() * 100 : 0);

        return analytics;
    }

    public Map<String, Object> getPaymentAnalytics(int days) {
        Map<String, Object> analytics = new HashMap<>();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Payment> payments = paymentRepository.findByPaymentDateBetween(startDateTime, endDateTime);

        // Payment status distribution
        Map<PaymentStatus, Long> paymentStatusCounts = payments.stream()
                .collect(Collectors.groupingBy(Payment::getStatus, Collectors.counting()));

        // Payment method distribution
        Map<String, Long> paymentMethodCounts = payments.stream()
                .collect(Collectors.groupingBy(
                        payment -> payment.getPaymentMethod() != null ? payment.getPaymentMethod().toString() : "UNKNOWN",
                        Collectors.counting()
                ));

        // Daily payment volume
        Map<LocalDate, Long> dailyPaymentVolume = payments.stream()
                .collect(Collectors.groupingBy(
                        payment -> payment.getPaymentDate().toLocalDate(),
                        Collectors.counting()
                ));

        // Success rate
        long successfulPayments = paymentStatusCounts.getOrDefault(PaymentStatus.COMPLETED, 0L);
        double successRate = !payments.isEmpty() ? (double) successfulPayments / payments.size() * 100 : 0;

        analytics.put("paymentStatusDistribution", paymentStatusCounts);
        analytics.put("paymentMethodDistribution", paymentMethodCounts);
        analytics.put("dailyPaymentVolume", dailyPaymentVolume);
        analytics.put("successRate", successRate);
        analytics.put("totalPayments", payments.size());

        return analytics;
    }

    public Map<String, Object> getRecentActivities(int limit) {
        Map<String, Object> activities = new HashMap<>();

        List<Booking> recentBookings = bookingRepository.findTop10ByOrderByBookingDateDesc();
        List<Payment> recentPayments = paymentRepository.findTop10ByOrderByPaymentDateDesc();

        List<Map<String, Object>> activityList = new ArrayList<>();

        // Add recent bookings
        for (Booking booking : recentBookings.stream().limit(limit / 2).toList()) {
            Map<String, Object> activity = new HashMap<>();
            activity.put("type", "booking");
            activity.put("message", "New booking " + booking.getBookingReference() + " created");
            
            // Handle null booking date
            LocalDateTime timestamp = booking.getBookingDate() != null 
                ? booking.getBookingDate().atStartOfDay() 
                : LocalDateTime.now();
            activity.put("timestamp", timestamp);
            
            activity.put("status", booking.getStatus().toString());
            activity.put("amount", booking.getTotalAmount());
            activityList.add(activity);
        }

        // Add recent payments
        for (Payment payment : recentPayments.stream().limit(limit / 2).toList()) {
            Map<String, Object> activity = new HashMap<>();
            activity.put("type", "payment");
            activity.put("message", "Payment " + payment.getVnpTxnRef() + " processed");
            
            // Handle null payment date
            LocalDateTime timestamp = payment.getPaymentDate() != null 
                ? payment.getPaymentDate() 
                : LocalDateTime.now();
            activity.put("timestamp", timestamp);
            
            activity.put("status", payment.getStatus().toString());
            activity.put("amount", payment.getAmount());
            activityList.add(activity);
        }

        // Sort by timestamp with null safety
        activityList.sort((a, b) -> {
            LocalDateTime timeA = (LocalDateTime) a.get("timestamp");
            LocalDateTime timeB = (LocalDateTime) b.get("timestamp");
            
            // Handle null timestamps
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            
            return timeB.compareTo(timeA);
        });

        activities.put("activities", activityList.stream().limit(limit).collect(Collectors.toList()));

        return activities;
    }

    public Map<String, Object> getBookingFunnel(int days) {
        Map<String, Object> funnel = new HashMap<>();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.plusDays(1).atStartOfDay();

        List<Booking> bookings = bookingRepository.findByBookingDateBetween(startDateTime.toLocalDate(), endDateTime.toLocalDate());

        // Booking funnel stages
        long totalBookings = bookings.size();
        long confirmedBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.PAID)
                .count();
        long pendingBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.PENDING_PAYMENT)
                .count();
        long cancelledBookings = bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CANCELLED)
                .count();

        // Conversion rates
        double confirmationRate = totalBookings > 0 ? (double) confirmedBookings / totalBookings * 100 : 0;
        double cancellationRate = totalBookings > 0 ? (double) cancelledBookings / totalBookings * 100 : 0;

        funnel.put("totalBookings", totalBookings);
        funnel.put("confirmedBookings", confirmedBookings);
        funnel.put("pendingBookings", pendingBookings);
        funnel.put("cancelledBookings", cancelledBookings);
        funnel.put("confirmationRate", confirmationRate);
        funnel.put("cancellationRate", cancellationRate);

        return funnel;
    }

    public Map<String, Object> getRealTimeMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Today's metrics
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        List<Booking> todaysBookings = bookingRepository.findByBookingDateBetween(startOfDay.toLocalDate(), endOfDay.toLocalDate());
        List<Payment> todaysPayments = paymentRepository.findByPaymentDateBetween(startOfDay, endOfDay);

        // Current hour metrics
        LocalDateTime currentHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime nextHour = currentHour.plusHours(1);

        List<Booking> currentHourBookings = bookingRepository.findByBookingDateBetween(currentHour.toLocalDate(), nextHour.toLocalDate());
        List<Payment> currentHourPayments = paymentRepository.findByPaymentDateBetween(currentHour, nextHour);

        metrics.put("todaysBookings", todaysBookings.size());
        metrics.put("todaysRevenue", todaysBookings.stream()
                .mapToDouble(booking -> booking.getTotalAmount() != null ? booking.getTotalAmount() : 0.0)
                .sum());
        metrics.put("todaysPayments", todaysPayments.size());
        metrics.put("currentHourBookings", currentHourBookings.size());
        metrics.put("currentHourPayments", currentHourPayments.size());
        metrics.put("timestamp", LocalDateTime.now());

        return metrics;
    }
}