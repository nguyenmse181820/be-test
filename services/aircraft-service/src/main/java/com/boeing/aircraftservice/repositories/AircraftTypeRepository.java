package com.boeing.aircraftservice.repositories;

import com.boeing.aircraftservice.pojos.Aircraft;
import com.boeing.aircraftservice.pojos.AircraftType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AircraftTypeRepository extends JpaRepository<AircraftType, UUID> {
    Optional<AircraftType> findByModelAndManufacturer(String model, String manufacturer);
    Page<AircraftType> findAllByDeletedFalse(Pageable pageable);
    List<AircraftType> findByDeletedIsFalse();
    AircraftType findAircraftTypeById(UUID id);
    AircraftType findAircraftTypeByModel(String model);
    AircraftType findAircraftTypeByManufacturer(String manufacturer);
    Page<AircraftType> findAll(Specification<AircraftType> spec, Pageable pageable);
}
