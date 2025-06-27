package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.dto.response.PaymentStatusDTO;
import com.boeing.bookingservice.exception.PaymentProcessingException;
import com.boeing.bookingservice.security.AuthenticatedUserPrincipal;
import com.boeing.bookingservice.service.impl.PaymentServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentServiceImpl paymentService;

    @PostMapping("/vnpay/create-url")
    @PreAuthorize("hasAuthority('USER')")
    public ResponseEntity<?> createPaymentUrl(@RequestBody CreatePaymentRequest request,
                                              HttpServletRequest httpRequest,
                                              Authentication authentication) {
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            
            String paymentUrl = paymentService.createVNPayPaymentUrl(request, userId);
            return ResponseEntity.ok(Map.of("paymentUrl", paymentUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/vn-pay-callback")
    public void handleCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        paymentService.processVNPayCallback(request, response);
    }

    @GetMapping("/status/{bookingReference}")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
    public ResponseEntity<PaymentStatusDTO> getPaymentStatus(@PathVariable String bookingReference,
                                                            Authentication authentication) {
        try {
            UUID userId = getUserIdFromAuthentication(authentication);
            
            PaymentStatusDTO status = paymentService.getPaymentStatus(bookingReference, userId);
            return ResponseEntity.ok(status);
        } catch (PaymentProcessingException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal) {
            return ((AuthenticatedUserPrincipal) principal).getUserIdAsUUID();
        }
        throw new AccessDeniedException("Cannot determine user ID from authentication principal.");
    }
}