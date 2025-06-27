package com.boeing.user.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {
    String email;
    String password;
    String firstName;
    String lastName;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate dob;
    
    String phone;
    String gender;
    String nationality;
    String role;
}