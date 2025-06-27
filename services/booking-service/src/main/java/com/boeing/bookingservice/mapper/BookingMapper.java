package com.boeing.bookingservice.mapper;

import com.boeing.bookingservice.dto.response.BookingFullDetailResponseDTO;
import com.boeing.bookingservice.dto.response.BookingInfoDTO;
import com.boeing.bookingservice.dto.response.BookingSummaryDTO;
import com.boeing.bookingservice.dto.response.BookingDetailInfoDTO;
import com.boeing.bookingservice.dto.response.FlightSummaryInBookingDTO;
import com.boeing.bookingservice.dto.response.PassengerSummaryDTO;
import com.boeing.bookingservice.dto.response.PaymentInfoForBookingDetailDTO;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.BookingDetail;
import com.boeing.bookingservice.model.entity.Passenger;
import com.boeing.bookingservice.model.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings; // Import nếu dùng nhiều @Mapping

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    BookingInfoDTO toBookingInfoDTO(Booking booking);

    @Mapping(source = "id", target = "passengerId")
    PassengerSummaryDTO toPassengerSummaryDTO(Passenger passenger);

    @Mappings({
            @Mapping(source = "id", target = "bookingDetailId"),
    })
    BookingDetailInfoDTO toBookingDetailInfoDTO(BookingDetail bookingDetail);

    @Mapping(source = "id", target = "paymentId")
    PaymentInfoForBookingDetailDTO toPaymentInfoForBookingDetailDTO(Payment payment);

    default BookingFullDetailResponseDTO toBookingFullDetailResponseDTO(Booking booking) {
        if (booking == null) {
            return null;
        }

        BookingInfoDTO bookingInfo = toBookingInfoDTO(booking);

        List<BookingDetailInfoDTO> details = booking.getBookingDetails() != null ?
                booking.getBookingDetails().stream()
                        .map(this::toBookingDetailInfoDTO)
                        .collect(Collectors.toList()) :
                null;

        List<PaymentInfoForBookingDetailDTO> payments = booking.getPayments() != null ?
                booking.getPayments().stream()
                        .map(this::toPaymentInfoForBookingDetailDTO)
                        .collect(Collectors.toList()) :
                null;

        return BookingFullDetailResponseDTO.builder()
                .bookingInfo(bookingInfo)
                .details(details)
                .payments(payments)
                .build();
    }

    @Mapping(target = "flightSummaries", expression = "java(mapFlightSummaries(booking.getBookingDetails()))")
    @Mapping(target = "passengerCount", expression = "java(calculatePassengerCount(booking.getBookingDetails()))")
    BookingSummaryDTO toBookingSummaryDTO(Booking booking);

    default List<FlightSummaryInBookingDTO> mapFlightSummaries(List<BookingDetail> bookingDetails) {
        if (bookingDetails == null || bookingDetails.isEmpty()) {
            return null;
        }
        return bookingDetails.stream()
                .map(detail -> FlightSummaryInBookingDTO.builder()
                        .flightCode(detail.getFlightCode())
                        .originAirportCode(detail.getOriginAirportCode())
                        .destinationAirportCode(detail.getDestinationAirportCode())
                        .departureTime(detail.getDepartureTime())
                        .build())
                .distinct()
                .collect(Collectors.toList());
    }

    default Integer calculatePassengerCount(List<BookingDetail> bookingDetails) {
        if (bookingDetails == null || bookingDetails.isEmpty()) {
            return 0;
        }
        return (int) bookingDetails.stream().map(BookingDetail::getPassenger).distinct().count();
    }
}