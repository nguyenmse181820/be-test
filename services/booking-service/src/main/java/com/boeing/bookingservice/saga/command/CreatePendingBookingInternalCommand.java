package com.boeing.bookingservice.saga.command;

import com.boeing.bookingservice.dto.request.BaggageAddonRequestDTO;
import com.boeing.bookingservice.dto.request.PassengerInfoDTO;
import com.boeing.bookingservice.dto.request.SeatSelectionDTO;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CreatePendingBookingInternalCommand {
    UUID sagaId;
    String bookingReference;
    UUID flightId;
    List<PassengerInfoDTO> passengersInfo;
    String selectedFareName;
    List<SeatSelectionDTO> seatSelections;
    UUID userId;
    Double totalAmount;
    Double discountAmount;
    String appliedVoucherCode;
    LocalDateTime paymentDeadline;
    String paymentMethod;
    String snapshotFlightCode;
    String snapshotOriginAirportCode;
    String snapshotDestinationAirportCode;
    LocalDateTime snapshotDepartureTime;
    LocalDateTime snapshotArrivalTime;
    String clientIpAddress;
    List<BaggageAddonRequestDTO> baggageAddons;
}