package com.boeing.loyalty.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.boeing.loyalty.dto.voucher.UserVoucherResponseDTO;
import com.boeing.loyalty.entity.LoyaltyPointTransaction;
import com.boeing.loyalty.entity.Membership;
import com.boeing.loyalty.entity.UserVoucher;
import com.boeing.loyalty.entity.VoucherTemplate;
import com.boeing.loyalty.entity.enums.MembershipTier;
import com.boeing.loyalty.entity.enums.PointType;
import com.boeing.loyalty.entity.enums.VoucherStatus;
import com.boeing.loyalty.exception.BadRequestException;
import com.boeing.loyalty.repository.LoyaltyPointTransactionRepository;
import com.boeing.loyalty.repository.MembershipRepository;
import com.boeing.loyalty.repository.UserVoucherRepository;
import com.boeing.loyalty.repository.VoucherTemplateRepository;

@ExtendWith(MockitoExtension.class)
class UserVoucherServiceTest {

    @Mock
    private UserVoucherRepository userVoucherRepository;
    
    @Mock
    private VoucherTemplateRepository voucherTemplateRepository;
    
    @Mock
    private VoucherTemplateService voucherTemplateService;
    
    @Mock
    private MembershipRepository membershipRepository;
    
    @Mock
    private LoyaltyPointTransactionRepository loyaltyPointTransactionRepository;

    @InjectMocks
    private UserVoucherService userVoucherService;

    @Test
    void redeemVoucher_ShouldSucceed_WhenUserHasEnoughPoints() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        
        VoucherTemplate template = VoucherTemplate.builder()
                .id(templateId)
                .code("TEST10")
                .name("Test Voucher")
                .pointsRequired(100)
                .status(VoucherStatus.ACTIVE)
                .endDate(LocalDate.now().plusDays(30))
                .build();
                
        Membership membership = Membership.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .points(200)
                .tier(MembershipTier.SILVER)
                .build();
                
        UserVoucher savedVoucher = UserVoucher.builder()
                .id(UUID.randomUUID())
                .membership(membership)
                .voucher(template)
                .code("VOUCHER123")
                .isUsed(false)
                .build();

        when(voucherTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(Membership.class))).thenReturn(membership);
        when(loyaltyPointTransactionRepository.save(any(LoyaltyPointTransaction.class))).thenReturn(new LoyaltyPointTransaction());
        when(userVoucherRepository.save(any(UserVoucher.class))).thenReturn(savedVoucher);

        // When
        UserVoucherResponseDTO result = userVoucherService.redeemVoucher(userId, templateId);

        // Then
        assertNotNull(result);
        verify(membershipRepository).save(argThat(m -> m.getPoints() == 100)); // 200 - 100 = 100
        verify(loyaltyPointTransactionRepository).save(argThat(t -> 
            t.getType() == PointType.REDEEM && 
            t.getPoints() == 100 &&
            t.getSource().equals("VOUCHER_REDEMPTION")
        ));
        verify(userVoucherRepository).save(any(UserVoucher.class));
    }

    @Test
    void redeemVoucher_ShouldThrowException_WhenUserHasInsufficientPoints() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        
        VoucherTemplate template = VoucherTemplate.builder()
                .id(templateId)
                .pointsRequired(200)
                .status(VoucherStatus.ACTIVE)
                .build();
                
        Membership membership = Membership.builder()
                .userId(userId)
                .points(50) // Less than required 200
                .build();

        when(voucherTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));
        when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(membership));

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> userVoucherService.redeemVoucher(userId, templateId));
            
        assertTrue(exception.getMessage().contains("Insufficient points"));
        verify(membershipRepository, never()).save(any());
        verify(loyaltyPointTransactionRepository, never()).save(any());
        verify(userVoucherRepository, never()).save(any());
    }

    @Test
    void redeemVoucher_ShouldThrowException_WhenTemplateIsInactive() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        
        VoucherTemplate template = VoucherTemplate.builder()
                .id(templateId)
                .pointsRequired(100)
                .status(VoucherStatus.INACTIVE)
                .build();

        when(voucherTemplateRepository.findById(templateId)).thenReturn(Optional.of(template));

        // When & Then
        BadRequestException exception = assertThrows(BadRequestException.class, 
            () -> userVoucherService.redeemVoucher(userId, templateId));
            
        assertEquals("Voucher template is not active", exception.getMessage());
    }
}
