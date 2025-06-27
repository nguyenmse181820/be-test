package com.boeing.aircraftservice.utils;

import com.boeing.aircraftservice.pojos.AircraftType;
import org.springframework.data.jpa.domain.Specification;

public class AircraftTypeSpecification {

    public static Specification<AircraftType> searchByField(String field, String value) {
        return (root, query, criteriaBuilder) -> {
            if (value == null || value.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }

            String pattern = "%" + value.toLowerCase() + "%";

            switch (field) {
                case "model":
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), pattern);
                case "manufacturer":
                    return criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), pattern);
                default:
                    return null;
            }
        };
    }

}
