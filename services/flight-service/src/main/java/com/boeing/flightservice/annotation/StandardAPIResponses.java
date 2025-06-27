package com.boeing.flightservice.annotation;

import com.boeing.flightservice.dto.common.APIResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Data retrieved successfully",
                content = @Content(schema = @Schema(implementation = APIResponse.class))
        ),
        @ApiResponse(
                responseCode = "201",
                description = "Created"
        ),
        @ApiResponse(
                responseCode = "400",
                description = "Bad Request — Validation failed or malformed input",
                content = @Content(schema = @Schema(implementation = APIResponse.class))
        ),
        @ApiResponse(
                responseCode = "401",
                description = "Unauthorized — Bad credentials or unauthenticated",
                content = @Content(schema = @Schema(implementation = APIResponse.class))
        ),
        @ApiResponse(
                responseCode = "403",
                description = "Forbidden — Access denied for the authenticated user",
                content = @Content(schema = @Schema(implementation = APIResponse.class))
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Internal Server Error — Unexpected runtime exception",
                content = @Content(schema = @Schema(implementation = APIResponse.class))
        )
})
public @interface StandardAPIResponses {
}
