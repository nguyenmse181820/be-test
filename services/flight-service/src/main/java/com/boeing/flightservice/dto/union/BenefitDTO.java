package com.boeing.flightservice.dto.union;

import com.boeing.flightservice.entity.Benefit;
import lombok.Builder;

import java.util.UUID;

@Builder
@SuppressWarnings("unused")
public record BenefitDTO(
) {

    @Builder
    public record CreateRequest(
            String name,
            String description,
            String iconURL
    ) {
    }

    @Builder
    public record UpdateRequest(
            String name,
            String description,
            String iconURL
    ) {
    }

    @Builder
    public record Response(
            UUID id,
            String name,
            String description,
            String iconURL
    ) {
    }

    public static Response fromEntity(Benefit entity) {
        return Response.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .iconURL(entity.getIconURL())
                .build();
    }
}
