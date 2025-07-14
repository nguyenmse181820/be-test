package com.boeing.bookingservice.saga.command;

import com.boeing.bookingservice.dto.request.BaggageAddonRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePendingMultiSegmentBookingInternalCommand {
    private UUID sagaId;
    private String bookingReference;
    private List<UUID> flightIds;
    private List<PassengerInfoDTO> passengersInfo;
    private String selectedFareName;
    private Map<String, List<String>> selectedSeatsByFlight;
    private UUID userId;
    private Double totalAmount;
    private Double discountAmount;
    private String appliedVoucherCode;
    private LocalDateTime paymentDeadline;
    private List<Map<String, Object>> flightDetails; // Contains all flight details for each segment
    private String paymentMethod;
    private String clientIpAddress;
    
    // Detailed seat pricing information from frontend
    private List<List<Map<String, Object>>> seatPricingByFlight;
    
    // Baggage add-ons
    private List<BaggageAddonRequestDTO> baggageAddons;
}
