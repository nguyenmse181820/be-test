package com.boeing.checkinservice.dto.responses;

import com.boeing.checkinservice.entity.enums.PassengerGender;
import com.boeing.checkinservice.entity.enums.PassengerTitle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerSummaryDTO {
    private UUID passengerId;
    private PassengerTitle title;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private PassengerGender gender;
    private String nationality;
}