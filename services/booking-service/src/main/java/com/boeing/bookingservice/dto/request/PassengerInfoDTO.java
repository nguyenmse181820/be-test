package com.boeing.bookingservice.dto.request;

import com.boeing.bookingservice.model.enums.PassengerGender;
import com.boeing.bookingservice.model.enums.PassengerTitle;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerInfoDTO {
    private Integer id;

    @NotBlank(message = "First name cannot be blank")
    private String firstName;

    @NotBlank(message = "Last name cannot be blank")
    private String lastName;

    private String familyName;

    @NotNull(message = "Date of birth cannot be null")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotNull(message = "Gender cannot be null")
    private PassengerGender gender;

    @NotBlank(message = "Nationality cannot be blank")
    private String nationality;

    @NotNull(message = "Title cannot be null")
    private PassengerTitle title;

    @NotBlank(message = "ID number (e.g., National ID) cannot be blank")
    private String idNumber;

    private String passportNumber;
    private String countryOfIssue;

    @FutureOrPresent(message = "Passport expiry date must be in the present or future if provided")
    private LocalDate passportExpiryDate;
}