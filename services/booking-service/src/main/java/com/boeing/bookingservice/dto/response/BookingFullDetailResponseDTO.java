package com.boeing.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingFullDetailResponseDTO {
    private BookingInfoDTO bookingInfo;
    private List<BookingDetailInfoDTO> details;
    private List<PaymentInfoForBookingDetailDTO> payments;
    private List<BaggageAddonResponseDTO> baggageAddons;
}