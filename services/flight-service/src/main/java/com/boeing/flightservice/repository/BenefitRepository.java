package com.boeing.flightservice.repository;

import com.boeing.flightservice.entity.Benefit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BenefitRepository extends JpaRepository<Benefit, UUID>, JpaSpecificationExecutor<Benefit> {
    Optional<Benefit> findByIdAndDeleted(UUID id, Boolean deleted);
}
