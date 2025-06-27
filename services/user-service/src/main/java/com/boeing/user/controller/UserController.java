package com.boeing.user.controller;

import com.boeing.user.dto.request.UpdateProfileRequest;
import com.boeing.user.dto.response.ApiResponse;
import com.boeing.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getAllUser() {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Get all users success")
                        .data(userService.findAll())
                        .build()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or @userServiceImpl.canUserAccessProfile(#id, authentication)")
    public ResponseEntity<ApiResponse<Object>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Get user " + id + " success")
                        .data(userService.findById(id))
                        .build()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN') or @userServiceImpl.canUserAccessProfile(#id, authentication)")
    public ResponseEntity<ApiResponse<Object>> updateUser(@PathVariable UUID id, @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Update profile " + id + " success")
                        .data(userService.updateProfile(id, request))
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> disableUser(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.builder()
                        .success(true)
                        .message("Disable profile " + id + " success")
                        .data(userService.disable(id))
                        .build()
        );
    }
}
