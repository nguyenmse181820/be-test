package com.boeing.aircraftservice.utils;

import com.boeing.aircraftservice.pojos.Aircraft;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class AircraftSpecification {

    public static Specification<Aircraft> searchByField(String field, String value) {
        return (root, query, criteriaBuilder) -> {
            if (value == null || value.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            String pattern = "%" + value.toLowerCase() + "%";

            switch (field) {
                case "code":
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), pattern);
                case "model":
                    return criteriaBuilder.like(
                            criteriaBuilder.lower(root.join("aircraftType", JoinType.LEFT).get("model")), pattern);
                case "manufacturer":
                    return criteriaBuilder.like(
                            criteriaBuilder.lower(root.join("aircraftType", JoinType.LEFT).get("manufacturer")),
                            pattern
                    );
                default:
                    return null;
            }
        };
    }

}
