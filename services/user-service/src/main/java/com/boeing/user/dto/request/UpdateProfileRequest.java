package com.boeing.user.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
 import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateProfileRequest {
    String email;
    String firstName;
    String lastName;
     LocalDate dob;
     String phone;
     String gender;
     String nationality;
}