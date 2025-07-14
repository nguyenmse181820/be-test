package com.boeing.bookingservice.repository.specifications;

import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.enums.BookingStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

public class BookingSpecifications {

    public static Specification<Booking> withFilters(
            BookingStatus status,
            String searchTerm,
            UUID userId,
            String flightCode,
            Double totalAmountMin,
            Double totalAmountMax,
            LocalDateTime dateFrom,
            LocalDateTime dateTo) {

        return (root, query, criteriaBuilder) -> {
            // Đảm bảo không có các bản ghi trùng lặp
            query.distinct(true);

            Predicate predicate = criteriaBuilder.conjunction(); // Bắt đầu với một điều kiện luôn đúng (AND true)

            if (status != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), status));
            }

            if (userId != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("userId"), userId));
            }

            if (StringUtils.hasText(searchTerm)) {
                String lowerSearchTerm = "%" + searchTerm.toLowerCase() + "%";
                Join<Booking, BookingDetail> bookingDetailJoin = root.join("bookingDetails");

                Predicate searchPredicate = criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("bookingReference")), lowerSearchTerm),
                        criteriaBuilder.like(criteriaBuilder.lower(bookingDetailJoin.get("flightCode")), lowerSearchTerm)
                );
                predicate = criteriaBuilder.and(predicate, searchPredicate);
            }

            if (StringUtils.hasText(flightCode)) {
                String lowerFlightCode = "%" + flightCode.toLowerCase() + "%";
                Join<Booking, BookingDetail> bookingDetailJoin = root.join("bookingDetails");

                predicate = criteriaBuilder.and(predicate, criteriaBuilder.like(criteriaBuilder.lower(bookingDetailJoin.get("flightCode")), lowerFlightCode));
            }

            if (totalAmountMin != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("totalAmount"), totalAmountMin));
            }

            if (totalAmountMax != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("totalAmount"), totalAmountMax));
            }

            if (dateFrom != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            }

            if (dateTo != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), dateTo));
            }

            // Thêm sắp xếp
            query.orderBy(criteriaBuilder.desc(root.get("createdAt")));

            return predicate;
        };
    }
}