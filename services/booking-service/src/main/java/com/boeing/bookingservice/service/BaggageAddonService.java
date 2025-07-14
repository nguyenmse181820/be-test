package com.boeing.bookingservice.service;

import com.boeing.bookingservice.dto.request.BaggageAddonRequestDTO;
import com.boeing.bookingservice.dto.response.BaggageAddonResponseDTO;

import java.util.List;
import java.util.UUID;

public interface BaggageAddonService {
    
    /**
     * Add baggage addons to an existing booking (post-booking purchase)
     */
    List<BaggageAddonResponseDTO> addBaggageToBooking(UUID bookingId, List<BaggageAddonRequestDTO> baggageAddons);
    
    /**
     * Get all baggage addons for a booking
     */
    List<BaggageAddonResponseDTO> getBaggageAddons(UUID bookingId);
    
    /**
     * Calculate total baggage addon cost for a booking
     */
    Double calculateTotalBaggageAddonCost(List<BaggageAddonRequestDTO> baggageAddons);
}
