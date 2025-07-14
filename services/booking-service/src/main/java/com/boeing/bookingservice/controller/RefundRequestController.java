package com.boeing.bookingservice.controller;

import com.boeing.bookingservice.model.entity.RefundRequest;
import com.boeing.bookingservice.service.RefundRequestService;
import com.boeing.bookingservice.repository.RefundRequestRepository;
import com.boeing.bookingservice.dto.request.ApproveRefundRequestDTO;
import com.boeing.bookingservice.security.AuthenticatedUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/refund-requests")
@RequiredArgsConstructor
@Slf4j
public class RefundRequestController {
    
    private final RefundRequestService refundRequestService;
    private final RefundRequestRepository refundRequestRepository;

    @GetMapping("/pending")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public ResponseEntity<List<RefundRequest>> getPendingRefundRequests(Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<RefundRequest> pendingRequests = refundRequestRepository.findByStatus("PENDING");
        return ResponseEntity.ok(pendingRequests);
    }
    
    @GetMapping("/{refundRequestId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public ResponseEntity<RefundRequest> getRefundRequest(@PathVariable String refundRequestId, Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        return refundRequestRepository.findByRefundRequestId(refundRequestId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/booking/{bookingReference}")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN')")
    public ResponseEntity<List<RefundRequest>> getRefundRequestsByBooking(@PathVariable String bookingReference, Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        String userRole = getUserRoleFromAuthentication(authentication);
        if (!"ADMIN".equals(userRole)) {
            refundRequestService.verifyUserCanAccessBookingRefunds(bookingReference, userId);
        }
        
        List<RefundRequest> requests = refundRequestRepository.findByBookingReference(bookingReference);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/{refundRequestId}/approve")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public ResponseEntity<String> approveRefundRequest(
            @PathVariable String refundRequestId,
            @RequestBody(required = false) ApproveRefundRequestDTO approveRequest,
            Authentication authentication) {
        
        UUID userId = getUserIdFromAuthentication(authentication);
        String userFullName = getFullNameFromAuthentication(authentication);
        
        try {
            // Update refund request status
            refundRequestService.updateRefundRequestStatus(refundRequestId, "APPROVED", userFullName);
            
            // Update additional info if provided
            if (approveRequest != null) {
                updateAdditionalRefundInfo(
                    refundRequestId, 
                    approveRequest.getTransactionProofUrl(), 
                    approveRequest.getNotes()
                );
            }
            
            return ResponseEntity.ok("Refund request approved successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error approving refund request: " + e.getMessage());
        }
    }
    
    @PostMapping("/{refundRequestId}/complete")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public ResponseEntity<String> completeRefundRequest(
            @PathVariable String refundRequestId,
            @RequestParam(required = false) String transactionProofUrl,
            @RequestParam(required = false) String notes,
            Authentication authentication) {
        
        UUID userId = getUserIdFromAuthentication(authentication);
        String userFullName = getFullNameFromAuthentication(authentication);
        
        try {
            refundRequestService.updateRefundRequestStatus(refundRequestId, "COMPLETED", userFullName);

            if (transactionProofUrl != null || notes != null) {
                updateAdditionalRefundInfo(refundRequestId, transactionProofUrl, notes);
            }
            
            return ResponseEntity.ok("Refund request completed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error completing refund request: " + e.getMessage());
        }
    }
    
    @PostMapping("/{refundRequestId}/reject")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public ResponseEntity<String> rejectRefundRequest(
            @PathVariable String refundRequestId,
            @RequestParam(required = false) String notes,
            Authentication authentication) {
        
        UUID userId = getUserIdFromAuthentication(authentication);
        String userFullName = getFullNameFromAuthentication(authentication);
        
        try {
            refundRequestService.updateRefundRequestStatus(refundRequestId, "REJECTED", userFullName);
            
            if (notes != null) {
                updateAdditionalRefundInfo(refundRequestId, null, notes);
            }
            
            return ResponseEntity.ok("Refund request rejected");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error rejecting refund request: " + e.getMessage());
        }
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('USER', 'ADMIN', 'STAFF')")
    public ResponseEntity<String> createRefundRequest(
            @RequestParam String bookingReference,
            @RequestParam String reason,
            Authentication authentication) {
        
        UUID userId = getUserIdFromAuthentication(authentication);
        String userFullName = getFullNameFromAuthentication(authentication);
        String userRole = getUserRoleFromAuthentication(authentication);
        
        try {
            if (!"ADMIN".equals(userRole)) {
                refundRequestService.verifyUserCanCreateRefundForBooking(bookingReference, userId);
            }
            
            String refundRequestId = refundRequestService.createRefundRequest(bookingReference, reason, userFullName);
            return ResponseEntity.ok(refundRequestId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error creating refund request: " + e.getMessage());
        }
    }
    
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'STAFF')")
    public ResponseEntity<List<RefundRequest>> getAllRefundRequests(
            @RequestParam(required = false) String status,
            Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        
        List<RefundRequest> requests;
        if (status != null && !status.isEmpty()) {
            requests = refundRequestRepository.findByStatus(status.toUpperCase());
        } else {
            requests = refundRequestRepository.findAll();
        }
        
        return ResponseEntity.ok(requests);
    }
    
    private void updateAdditionalRefundInfo(String refundRequestId, String transactionProofUrl, String notes) {
        refundRequestRepository.findByRefundRequestId(refundRequestId)
                .ifPresent(request -> {
                    if (transactionProofUrl != null) {
                        request.setTransactionProofUrl(transactionProofUrl);
                    }
                    if (notes != null) {
                        request.setNotes(notes);
                    }
                    refundRequestRepository.save(request);
                });
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
    
    private String getFullNameFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
            String firstName = userPrincipal.getFirstName();
            String lastName = userPrincipal.getLastName();
            
            if (firstName != null && lastName != null) {
                return firstName + " " + lastName;
            } else if (firstName != null) {
                return firstName;
            } else if (lastName != null) {
                return lastName;
            } else {
                return userPrincipal.getUsername();
            }
        }
        throw new AccessDeniedException("Cannot determine user name from authentication principal.");
    }
    
    private String getUserRoleFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User is not authenticated.");
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.equals("USER") || authority.equals("ADMIN") || authority.equals("STAFF"))
                .findFirst()
                .orElse("USER");
    }
}
