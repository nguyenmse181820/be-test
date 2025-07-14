package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.dto.request.BaggageAddonRequestDTO;
import com.boeing.bookingservice.dto.response.BaggageAddonResponseDTO;
import com.boeing.bookingservice.exception.ResourceNotFoundException;
import com.boeing.bookingservice.mapper.BaggageAddonMapper;
import com.boeing.bookingservice.model.entity.BaggageAddon;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.enums.BaggageAddonType;
import com.boeing.bookingservice.repository.BaggageAddonRepository;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.service.BaggageAddonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BaggageAddonServiceImpl implements BaggageAddonService {

    private final BaggageAddonRepository baggageAddonRepository;
    private final BookingRepository bookingRepository;
    private final BaggageAddonMapper baggageAddonMapper;

    @Override
    @Transactional
    public List<BaggageAddonResponseDTO> addBaggageToBooking(UUID bookingId, List<BaggageAddonRequestDTO> baggageAddons) {
        log.info("Adding {} baggage addons to booking {}", baggageAddons.size(), bookingId);
        
        // Verify booking exists and is in a valid state for adding baggage
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found with id: " + bookingId));
        
        // Create baggage addon entities
        List<BaggageAddon> baggageAddonEntities = baggageAddons.stream()
                .map(dto -> createBaggageAddonEntity(dto, booking, true))
                .collect(Collectors.toList());
        
        // Save entities
        List<BaggageAddon> savedAddons = baggageAddonRepository.saveAll(baggageAddonEntities);
        
        log.info("Successfully added {} baggage addons to booking {}", savedAddons.size(), bookingId);
        
        return savedAddons.stream()
                .map(baggageAddonMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BaggageAddonResponseDTO> getBaggageAddons(UUID bookingId) {
        List<BaggageAddon> addons = baggageAddonRepository.findByBookingId(bookingId);
        return addons.stream()
                .map(baggageAddonMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Double calculateTotalBaggageAddonCost(List<BaggageAddonRequestDTO> baggageAddons) {
        if (baggageAddons == null || baggageAddons.isEmpty()) {
            return 0.0;
        }
        
        return baggageAddons.stream()
                .mapToDouble(BaggageAddonRequestDTO::getPrice)
                .sum();
    }

    /**
     * Create baggage addon entities during booking process
     */
    public List<BaggageAddon> createBaggageAddonsForBooking(List<BaggageAddonRequestDTO> baggageAddons, Booking booking) {
        if (baggageAddons == null || baggageAddons.isEmpty()) {
            return List.of();
        }
        
        return baggageAddons.stream()
                .map(dto -> createBaggageAddonEntity(dto, booking, false))
                .collect(Collectors.toList());
    }

    private BaggageAddon createBaggageAddonEntity(BaggageAddonRequestDTO dto, Booking booking, boolean isPostBooking) {
        return BaggageAddon.builder()
                .booking(booking)
                .passengerIndex(dto.getPassengerIndex())
                .baggageWeight(dto.getWeight())
                .price(dto.getPrice())
                .flightId(dto.getFlightId())
                .type(BaggageAddonType.valueOf(dto.getType().toUpperCase()))
                .purchaseTime(LocalDateTime.now())
                .isPostBooking(isPostBooking)
                .build();
    }
}
