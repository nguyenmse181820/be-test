package com.boeing.user.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
public class NotificationDto {

    private String content;

    private LocalDateTime sendAt;

}
