package com.boeing.checkinservice.service.impl;

import com.boeing.checkinservice.dto.requests.AddBoardingPassDto;
import com.boeing.checkinservice.dto.requests.BaggageDto;
import com.boeing.checkinservice.dto.responses.*;
import com.boeing.checkinservice.entity.Baggage;
import com.boeing.checkinservice.entity.BoardingPass;
import com.boeing.checkinservice.entity.enums.BaggageStatus;
import com.boeing.checkinservice.repository.BaggageRepository;
import com.boeing.checkinservice.repository.BoardingPassRepository;
import com.boeing.checkinservice.service.clients.BookingClient;
import com.boeing.checkinservice.service.clients.FlightClient;
import com.boeing.checkinservice.service.inte.BoardingPassService;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeMap;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BoardingPassServiceImpl implements BoardingPassService {

    private final BoardingPassRepository boardingPassRepository;

    private final ModelMapper mapper;

//    private final FlightClient flightClient;

    private final BookingClient bookingClient;
    private final BaggageRepository baggageRepository;

    @Override
    public Optional<?> getAllBoardingPassesByBookingReference(String bookingReference) {
        BookingFullDetailResponseDTO dto = bookingClient.getBookingDetails(bookingReference).getData();

        if (dto == null || dto.getDetails().isEmpty()) {
            return Optional.empty();
        }

        List<BookingDetailInfoDTO> detailDtos = dto.getDetails();
        List<BaggageAddonResponseDTO> baggageAddons = dto.getBaggageAddons();

        List<BoardingPassDto> response = detailDtos.stream()
                .map(detail -> {
                    BoardingPass boardingPass = boardingPassRepository.findByBookingDetailId(detail.getBookingDetailId());
                    if (boardingPass == null) return null;

                    // Lấy danh sách baggage tương ứng
                    List<BaggageDtoResponse> baggageResponses = boardingPass.getBaggage().stream()
                            .map(baggage -> {
                                // Tìm baggage addon theo id (nếu cần)
                                BaggageAddonResponseDTO matchedAddon = baggageAddons.stream()
                                        .filter(addon -> addon.getId().equals(baggage.getId()))
                                        .findFirst()
                                        .orElse(null);

                                return mapToBaggageDtoResponse(baggage, matchedAddon);
                            }).toList();

                    return mapToDto(boardingPass, detail, baggageResponses);
                })
                .filter(Objects::nonNull)
                .toList();

        return Optional.of(response);
    }



    private BoardingPassDto mapToDto(BoardingPass entity, BookingDetailInfoDTO dto, List<BaggageDtoResponse> baggageList) {
        return BoardingPassDto.builder()
                .id(entity.getId())
                .barcode(entity.getBarcode())
                .boardingTime(entity.getBoardingTime())
                .issuedAt(entity.getIssuedAt())
                .checkinTime(entity.getCheckinTime())
                .seatCode(entity.getSeatCode())
                .from(dto.getOriginAirportCode())
                .to(dto.getDestinationAirportCode())
                .arrivalTime(dto.getArrivalTime())
                .classType(dto.getSelectedFareName())
                .passengerName(dto.getPassenger().getFirstName() + " " + dto.getPassenger().getLastName())
                .title(dto.getPassenger().getTitle().name())
                .flightCode(dto.getFlightCode())
                .baggages(baggageList)
                .build();
    }


    private BaggageDtoResponse mapToBaggageDtoResponse(Baggage entity, BaggageAddonResponseDTO baggageDtoResponse) {
        return BaggageDtoResponse.builder()
                .id(entity.getId())
                .weight(entity.getWeight())
                .tagCode(entity.getTagCode())
                .type(baggageDtoResponse.getType().name())
                .build();
    }

    @Override
    public Optional<?> getBoardingPassById(UUID id) {
        BoardingPass entity = boardingPassRepository.findById(id).orElseThrow(() -> new BadRequestException("Not found any Boarding Pass"));

        BoardingPassDto dto = mapper.map(entity, BoardingPassDto.class);
        return Optional.of(dto);
    }

    @Override
    public Optional<String> addNewBoardingPass(List<AddBoardingPassDto> dtoList) {
        LocalDateTime now = LocalDateTime.now();

        for (AddBoardingPassDto dto : dtoList) {
            LocalDateTime departureTime = dto.getDeparture_time();

            if (now.isAfter(departureTime.minusMinutes(30))) {
                throw new IllegalArgumentException("Check-in must be at least 30 minutes before departure time for seat " + dto.getSeatCode());
            }

            String barcode = generateBarcode(dto.getFlightId(), dto.getSeatCode());

            BoardingPass boardingPass = BoardingPass.builder()
                    .boardingTime(departureTime)
                    .seatCode(dto.getSeatCode())
                    .checkinTime(now)
                    .bookingDetailId(dto.getBooking_detail_id())
                    .issuedAt(now)
                    .barcode(barcode)
                    .build();

            BoardingPass saved = boardingPassRepository.save(boardingPass);

            if(dto.getBaggage() != null) {
                for(BaggageDto baggage : dto.getBaggage()){
                    if(dtoList.indexOf(dto) == baggage.getPassengerIndex()){
                        Baggage entity = Baggage.builder()
                                .id(UUID.fromString(baggage.getBaggageAddOnsId()))
                                .boardingPass(saved)
                                .tagCode(baggage.getTagCode())
                                .weight(baggage.getWeight())
                                .status(BaggageStatus.TAGGED)
                                .build();
                        baggageRepository.save(entity);
                    }
                }
            }
        }

        return Optional.of("Created " + dtoList.size() + " boarding pass(es) successfully.");
    }




    private String generateBarcode(UUID flightId, String seatCode) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return "BAR-" + flightId.toString().substring(0, 8).toUpperCase()
                + "-" + seatCode.toUpperCase()
                + "-" + timestamp;
    }

    @Override
    public Optional<?> checkInStatus(UUID booking_detail_id) {
        Boolean isChecked = boardingPassRepository.existsByBookingDetailId(booking_detail_id);
        return Optional.of(isChecked);
    }




}
