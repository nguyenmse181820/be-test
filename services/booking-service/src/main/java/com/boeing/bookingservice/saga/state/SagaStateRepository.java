package com.boeing.bookingservice.saga.state;

import com.boeing.bookingservice.saga.SagaStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, UUID> {
    List<SagaState> findByCurrentStepIn(List<SagaStep> steps);
}