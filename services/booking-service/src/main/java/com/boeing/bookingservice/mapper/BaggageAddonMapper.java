package com.boeing.bookingservice.mapper;

import com.boeing.bookingservice.dto.response.BaggageAddonResponseDTO;
import com.boeing.bookingservice.model.entity.BaggageAddon;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BaggageAddonMapper {
    
    BaggageAddonResponseDTO toResponseDTO(BaggageAddon baggageAddon);
}
