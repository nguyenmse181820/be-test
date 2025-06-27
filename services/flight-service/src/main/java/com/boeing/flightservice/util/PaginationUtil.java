package com.boeing.flightservice.util;

import com.boeing.flightservice.dto.common.APIResponse;
import com.boeing.flightservice.dto.common.PagingResponse;
import com.boeing.flightservice.exception.BadRequestException;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJacksonValue;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class PaginationUtil {

    public static final ModelMapper modelMapper = new ModelMapper();

    public static <E, D> MappingJacksonValue findAll(
            Map<String, String> params,
            JpaSpecificationExecutor<E> repository,
            Class<D> dtoClass
    ) {
        // Sorting
        Sort sort = Sort.unsorted();
        if (params.containsKey("sortBy")) {
            String[] sortParameters = params.get("sortBy").split(",");
            List<Sort.Order> sortingOrders = new ArrayList<>();
            for (String sortParameter : sortParameters) {
                String[] split = sortParameter.split(":");
                String fieldName = split[0];
                Sort.Direction direction = split.length > 1 && split[1].equalsIgnoreCase("desc")
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC;
                sortingOrders.add(new Sort.Order(direction, fieldName));
            }
            sort = Sort.by(sortingOrders);
        }

        // Paging
        Pageable pageable = Pageable.unpaged(sort);
        if (params.containsKey("pageNo") && params.containsKey("pageSize")) {
            int pageNo = Integer.parseInt(params.get("pageNo"));
            int pageSize = Integer.parseInt(params.get("pageSize"));
            pageable = PageRequest.of(pageNo - 1, pageSize, sort);
        }

        // Dynamic Specification
        Specification<E> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            try {
                predicates.add(criteriaBuilder.equal(root.get("deleted"), false));
            } catch (IllegalArgumentException e) {
                log.warn("'deleted' field not found on entity: {}", e.getMessage());
            }

            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key.equals("pageNo") || key.equals("pageSize") || key.equals("sortBy") || key.equals("params")) {
                    continue;
                }

                try {
                    Class<?> fieldType = root.get(key).getJavaType();
                    switch (fieldType.getSimpleName()) {
                        case "String":
                            predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(key)), "%" + value.toLowerCase() + "%"));
                            break;
                        case "Integer":
                        case "Long":
                            predicates.add(criteriaBuilder.equal(root.get(key), Integer.parseInt(value)));
                            break;
                        case "UUID":
                            predicates.add(criteriaBuilder.equal(root.get(key), UUID.fromString(value)));
                            break;
                        case "LocalDateTime":
                            String[] dateRange = value.split(",", -1); // preserve empty strings
                            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

                            try {
                                if (!dateRange[0].isEmpty()) {
                                    LocalDateTime start = LocalDateTime.parse(dateRange[0], formatter);
                                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get(key), start));
                                }
                                if (dateRange.length > 1 && !dateRange[1].isEmpty()) {
                                    LocalDateTime end = LocalDateTime.parse(dateRange[1], formatter);
                                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get(key), end));
                                }
                            } catch (DateTimeParseException e) {
                                log.error("Invalid LocalDateTime format for key '{}': {}", key, value);
                                throw new BadRequestException("Invalid date format for key" + key + ":" + value);
                            }
                            break;
                    }
                } catch (IllegalArgumentException e) {
                    log.error("Error specification: {}", e.getMessage());
                    throw new BadRequestException("Error specification: " + e.getMessage());
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        // Fetching data from a repository
        List<E> data = repository.findAll(specification, pageable).getContent();
        List<D> dtoList = data
                .stream()
                .map(e -> modelMapper.map(e, dtoClass))
                .toList();
        PagingResponse pagingResponse = PagingResponse.builder()
                .content(dtoList)
                .options(!params.isEmpty() ? params : "No parameters provided")
                .totalElements(dtoList.size())
                .totalPages(pageable.isPaged() ? pageable.getPageNumber() + 1 : 1)
                .build();
        APIResponse response = APIResponse.builder()
                .statusCode(HttpStatus.OK.value())
                .data(pagingResponse)
                .build();

        // Dynamic filters
        SimpleFilterProvider filters = new SimpleFilterProvider().addFilter("dynamicFilter", SimpleBeanPropertyFilter.serializeAll());
        if (params.containsKey("params")) {
            String[] serializedParams = params.get("params").replaceAll(" ", "").split(",");
            filters = new SimpleFilterProvider().addFilter("dynamicFilter", SimpleBeanPropertyFilter.filterOutAllExcept(serializedParams));
        }

        MappingJacksonValue mapping = new MappingJacksonValue(response);
        mapping.setFilters(filters);
        return mapping;
    }

}
