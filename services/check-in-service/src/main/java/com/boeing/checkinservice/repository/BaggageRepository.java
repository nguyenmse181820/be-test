package com.boeing.checkinservice.repository;

import com.boeing.checkinservice.entity.Baggage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BaggageRepository extends JpaRepository<Baggage, UUID> {
}
