package com.boeing.user.controller;

import com.boeing.user.dto.request.*;
import com.boeing.user.dto.response.ApiResponse;
import com.boeing.user.service.IdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/identity")
public class IdentityController {

    private final IdentityService identityService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Object>> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Register user success")
                        .data(identityService.registerUser(request))
                        .build()
        );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Object>> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Login success")
                        .data(identityService.login(request))
                        .build()
        );
    }

    @PostMapping("/forget-password")
    public ResponseEntity<ApiResponse<Object>> forgetPasswordRequest(@RequestBody ForgetPasswordRequest request) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Request verify otp success")
                        .data(identityService.forgetPasswordRequest(request))
                        .build()
        );
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<Object>> verifyOtp(@RequestBody VerifyOTP request) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Verify otp success")
                        .data(identityService.verifyOTP(request))
                        .build()
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Object>> resetPassword(@RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Reset password success")
                        .data(identityService.resetPassword(request))
                        .build()
        );
    }
}
