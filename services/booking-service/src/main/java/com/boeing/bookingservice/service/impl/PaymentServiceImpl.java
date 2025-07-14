package com.boeing.bookingservice.service.impl;

import com.boeing.bookingservice.config.VNPAYConfig;
import com.boeing.bookingservice.dto.request.CreatePaymentRequest;
import com.boeing.bookingservice.dto.response.PaymentDTO;
import com.boeing.bookingservice.dto.response.PaymentStatusDTO;
import com.boeing.bookingservice.event.PaymentProcessedEvent;
import com.boeing.bookingservice.exception.PaymentProcessingException;
import com.boeing.bookingservice.model.entity.Booking;
import com.boeing.bookingservice.model.entity.Payment;
import com.boeing.bookingservice.model.enums.PaymentMethod;
import com.boeing.bookingservice.model.enums.PaymentStatus;
import com.boeing.bookingservice.model.enums.PaymentType;
import com.boeing.bookingservice.repository.BookingRepository;
import com.boeing.bookingservice.repository.PaymentRepository;
import com.boeing.bookingservice.service.BookingService;
import com.boeing.bookingservice.service.PaymentService;
import com.boeing.bookingservice.service.RescheduleService;
import com.boeing.bookingservice.utils.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {
    private final VNPAYConfig vnPayConfig;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RescheduleService rescheduleService;

    @Value("${frontend.url}")
    private String frontendUrl;

    public PaymentServiceImpl(VNPAYConfig vnPayConfig,
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            @Lazy BookingService bookingService,
            ApplicationEventPublisher eventPublisher,
            @Lazy RescheduleService rescheduleService) {
        this.vnPayConfig = vnPayConfig;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.eventPublisher = eventPublisher;
        this.rescheduleService = rescheduleService;
    }

    @Override
    @Transactional
    public Payment createPayment(CreatePaymentRequest request, UUID userId) {
        // Verify user owns the booking
        verifyBookingOwnership(request.getBookingReference(), userId);

        Booking booking = bookingRepository.findByBookingReference(request.getBookingReference())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Booking not found with reference: " + request.getBookingReference()));

        // Create initial pending payment record
        Payment payment = Payment.builder()
                .orderCode(generateOrderCode())
                .bookingReference(request.getBookingReference())
                .booking(booking)
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .paymentType(PaymentType.BOOKING_INITIAL)
                .currency("VND")
                .paymentMethod(PaymentMethod.valueOf(request.getPaymentMethod()))
                .description("Initial payment for booking: " + request.getBookingReference())
                .build();

        return paymentRepository.save(payment);
    }

    @Override
    public String createVNPayPaymentUrl(CreatePaymentRequest request, UUID userId) {
        log.info("[VNPAY_URL_GENERATION] Starting VNPay URL generation for booking: {}, userId: {}, paymentMethod: {}",
                request.getBookingReference(), userId, request.getPaymentMethod());

        // Check if payment method supports VNPay
        if (request.getPaymentMethod() == null) {
            log.error("[VNPAY_URL_GENERATION] Payment method is null for booking: {}. This should not happen after controller validation.", 
                    request.getBookingReference());
            // Default to VNPAY_BANKTRANSFER as a fallback
            log.warn("[VNPAY_URL_GENERATION] Defaulting payment method to VNPAY_BANKTRANSFER");
            request.setPaymentMethod("VNPAY_BANKTRANSFER");
        }

        String paymentMethod = request.getPaymentMethod().toUpperCase();
        if (!paymentMethod.startsWith("VNPAY")) {
            log.warn("[VNPAY_URL_GENERATION] Payment method '{}' is not a VNPay method for booking: {}",
                    request.getPaymentMethod(), request.getBookingReference());
            return "";
        }

        // Verify user owns the booking
        verifyBookingOwnership(request.getBookingReference(), userId);

        log.info("[VNPAY_URL_GENERATION] User ownership verified for booking: {}", request.getBookingReference());

        PaymentDTO.VNPayResponse response = requestPayment(
                request.getAmount(),
                request.getBankCode(),
                request.getBookingReference(),
                null);

        log.info("[VNPAY_URL_GENERATION] Payment response received - Code: {}, Message: {}, PaymentUrl length: {}",
                response.getCode(), response.getMessage(),
                response.getPaymentUrl() != null ? response.getPaymentUrl().length() : "null");

        if (response.getPaymentUrl() == null || response.getPaymentUrl().trim().isEmpty()) {
            log.error("[VNPAY_URL_GENERATION] Generated payment URL is null or empty for booking: {}",
                    request.getBookingReference());
        } else {
            log.info("[VNPAY_URL_GENERATION] Successfully generated payment URL for booking: {}, URL: {}",
                    request.getBookingReference(), response.getPaymentUrl());
        }

        return response.getPaymentUrl();
    }

    public PaymentDTO.VNPayResponse requestPayment(
            Double amount,
            String bankCode,
            String bookingReference,
            HttpServletRequest request) {
        Map<String, String> vnpParamsMap = vnPayConfig.getVNPayConfig();

        long amountInVND = Math.round(amount * 100);
        vnpParamsMap.put("vnp_Amount", String.valueOf(amountInVND));

        if (bankCode != null && !bankCode.isEmpty()) {
            vnpParamsMap.put("vnp_BankCode", bankCode);
        }

        if (request != null) {
            vnpParamsMap.put("vnp_IpAddr", VNPayUtil.getIpAddress(request));
        } else {
            vnpParamsMap.put("vnp_IpAddr", "127.0.0.1");
        }

        vnpParamsMap.put("vnp_OrderInfo", "Thanh_toan_don_hang_" + bookingReference);
        vnpParamsMap.put("vnp_TxnRef", bookingReference);

        return getVnPayResponse(vnpParamsMap);
    }

    private PaymentDTO.VNPayResponse getVnPayResponse(Map<String, String> vnpParamsMap) {
        log.info("[VNPAY_URL_BUILD] Building VNPay URL with params count: {}", vnpParamsMap.size());
        log.debug("[VNPAY_URL_BUILD] VNPay params: {}", vnpParamsMap);

        String queryUrl = VNPayUtil.getPaymentURL(vnpParamsMap, true);
        log.info("[VNPAY_URL_BUILD] Query URL generated, length: {}", queryUrl != null ? queryUrl.length() : "null");

        String hashData = VNPayUtil.getPaymentURL(vnpParamsMap, false);
        log.info("[VNPAY_URL_BUILD] Hash data generated, length: {}", hashData != null ? hashData.length() : "null");

        String vnpSecureHash = VNPayUtil.hmacSHA512(vnPayConfig.getSecretKey(), hashData);
        log.info("[VNPAY_URL_BUILD] Secure hash generated, length: {}",
                vnpSecureHash != null ? vnpSecureHash.length() : "null");

        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
        String paymentUrl = vnPayConfig.getVnp_PayUrl() + "?" + queryUrl;

        log.info("[VNPAY_URL_BUILD] Final payment URL generated - Base URL: {}, Full URL length: {}",
                vnPayConfig.getVnp_PayUrl(), paymentUrl != null ? paymentUrl.length() : "null");
        log.info("[VNPAY_URL_BUILD] Complete payment URL: {}", paymentUrl);

        return PaymentDTO.VNPayResponse.builder()
                .code("ok")
                .message("success")
                .paymentUrl(paymentUrl)
                .build();
    }

    public void processVNPayCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String txnRef = request.getParameter("vnp_TxnRef");
        String transactionNo = request.getParameter("vnp_TransactionNo");
        String amount = request.getParameter("vnp_Amount");
        String responseCode = request.getParameter("vnp_ResponseCode");

        log.info("[VNPAY_CALLBACK] Processing callback for booking: {}, txnNo: {}, responseCode: {}",
                txnRef, transactionNo, responseCode);

        try {
            if (!validateCallbackSignature(request)) {
                log.error("[VNPAY_CALLBACK] Invalid signature for booking: {}", txnRef);
                eventPublisher
                        .publishEvent(new PaymentProcessedEvent(this, txnRef, false, "Invalid payment signature"));
                redirectToFailurePage(response, "Invalid payment signature");
                return;
            }

            if ("00".equals(responseCode)) {
                log.info("[VNPAY_CALLBACK] Payment successful for booking: {}, proceeding with payment completion",
                        txnRef);

                // Check if this is a reschedule payment
                if (txnRef.contains("_RESCHEDULE_")) {
                    log.info("[VNPAY_CALLBACK] This is a reschedule payment: {}", txnRef);
                    // Process reschedule payment
                    UUID rescheduleHistoryId = rescheduleService.completeRescheduleAfterPayment(txnRef, transactionNo);
                    log.info("[VNPAY_CALLBACK] Reschedule completed with historyId: {}", rescheduleHistoryId);

                    // Create payment record for reschedule
                    createReschedulePaymentRecord(txnRef, transactionNo, amount, request);
                } else {
                    // Regular booking payment
                    handleSuccessfulPayment(txnRef, transactionNo, amount, request);
                }

                eventPublisher.publishEvent(new PaymentProcessedEvent(this, txnRef, true, transactionNo));
                log.info("[VNPAY_CALLBACK] Published PaymentProcessedEvent(success=true) for booking: {}", txnRef);
                redirectToSuccessPage(response, txnRef, transactionNo, amount);
            } else {
                log.warn("[VNPAY_CALLBACK] Payment failed for booking: {}, responseCode: {}", txnRef, responseCode);
                eventPublisher.publishEvent(
                        new PaymentProcessedEvent(this, txnRef, false, "Payment failed with code: " + responseCode));
                redirectToFailurePage(response, "Payment failed with code: " + responseCode);
            }

        } catch (Exception e) {
            log.error("[VNPAY_CALLBACK] Exception processing callback for booking: {}", txnRef, e);
            eventPublisher.publishEvent(new PaymentProcessedEvent(this, txnRef, false, "Error processing payment"));
            redirectToFailurePage(response, "Error processing payment");
        }
    }

    /**
     * Create payment record for a reschedule transaction
     */
    @Transactional
    protected void createReschedulePaymentRecord(String txnRef, String transactionNo, String amount,
                                                 HttpServletRequest request) {
        log.info("[VNPAY_CALLBACK] Creating payment record for reschedule: {}", txnRef);

        // Extract original booking reference
        String originalBookingRef = txnRef.split("_RESCHEDULE_")[0];

        Booking booking = bookingRepository.findByBookingReference(originalBookingRef)
                .orElseThrow(() -> new PaymentProcessingException("Original booking not found: " + originalBookingRef));

        // Create a payment record for the reschedule
        Payment payment = Payment.builder()
                .orderCode(generateOrderCode())
                .bookingReference(txnRef) // Store the full reference with RESCHEDULE suffix
                .booking(booking) // Link to the original booking
                .amount(Double.parseDouble(amount) / 100)
                .vnpTxnRef(txnRef)
                .status(PaymentStatus.COMPLETED)
                .paymentType(PaymentType.RESCHEDULE_ADDITIONAL) // Additional fee for reschedule
                .currency("VND")
                .paymentMethod(PaymentMethod.VNPAY_BANKTRANSFER)
                .vnpResponseCode(request.getParameter("vnp_ResponseCode"))
                .vnpTransactionNo(transactionNo)
                .transactionId(transactionNo)
                .description("VNPay payment for reschedule: " + originalBookingRef)
                .paymentDate(LocalDateTime.now())
                .build();

        paymentRepository.save(payment);
        log.info("[VNPAY_CALLBACK] Reschedule payment record created for: {}", txnRef);
    }

    @Transactional
    protected void handleSuccessfulPayment(String txnRef, String transactionNo, String amount,
            HttpServletRequest request) {
        Booking booking = bookingRepository.findByBookingReference(txnRef)
                .orElseThrow(() -> new PaymentProcessingException("Booking not found: " + txnRef));

        // First try to find the initial payment record by booking and BOOKING_INITIAL
        // type
        Optional<Payment> initialPayment = paymentRepository.findByBookingAndPaymentType(booking,
                PaymentType.BOOKING_INITIAL);

        Payment payment;
        if (initialPayment.isPresent()) {
            // Update the existing initial payment record
            payment = initialPayment.get();

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaymentType(PaymentType.BOOKING); // Change from BOOKING_INITIAL to BOOKING
            payment.setVnpTxnRef(txnRef);
            payment.setVnpResponseCode(request.getParameter("vnp_ResponseCode"));
            payment.setVnpTransactionNo(transactionNo);
            payment.setTransactionId(transactionNo);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setPaymentMethod(PaymentMethod.VNPAY_BANKTRANSFER);

            // Update amount from VNPay callback (convert from VND cents to VND)
            Double callbackAmount = Double.parseDouble(amount) / 100;
            payment.setAmount(callbackAmount);
            payment.setDescription("VNPay payment for booking: " + txnRef);

        } else {
            // Fallback: create new payment if no initial payment found (shouldn't happen in
            // normal flow)
            payment = Payment.builder()
                    .orderCode(generateOrderCode())
                    .bookingReference(txnRef)
                    .booking(booking)
                    .amount(Double.parseDouble(amount) / 100)
                    .vnpTxnRef(txnRef)
                    .status(PaymentStatus.COMPLETED)
                    .paymentType(PaymentType.BOOKING)
                    .currency("VND")
                    .paymentMethod(PaymentMethod.VNPAY_BANKTRANSFER)
                    .vnpResponseCode(request.getParameter("vnp_ResponseCode"))
                    .vnpTransactionNo(transactionNo)
                    .transactionId(transactionNo)
                    .description("VNPay payment for booking: " + txnRef)
                    .paymentDate(LocalDateTime.now())
                    .build();
        }

        payment = paymentRepository.save(payment);
    }

    private boolean validateCallbackSignature(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values.length > 0) {
                params.put(key, values[0]);
            }
        });

        String receivedHash = params.get("vnp_SecureHash");
        params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        String hashData = VNPayUtil.getPaymentURL(params, false);
        String calculatedHash = VNPayUtil.hmacSHA512(vnPayConfig.getSecretKey(), hashData);

        return calculatedHash.equals(receivedHash);
    }

    @Override
    public PaymentStatusDTO getPaymentStatus(String bookingReference, UUID userId) {
        // Verify user owns the booking
        verifyBookingOwnership(bookingReference, userId);

        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new PaymentProcessingException("Booking not found: " + bookingReference));

        Optional<Payment> paymentOpt = paymentRepository.findByBooking(booking);

        PaymentStatusDTO.PaymentStatusDTOBuilder responseBuilder = PaymentStatusDTO.builder()
                .bookingReference(bookingReference)
                .bookingStatus(booking.getStatus().toString());

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            responseBuilder.paymentStatus(payment.getStatus().toString())
                    .amount(payment.getAmount())
                    .transactionNo(payment.getVnpTransactionNo())
                    .paymentDate(payment.getCreatedAt())
                    .paymentMethod(payment.getPaymentMethod().toString());
        } else {
            responseBuilder.paymentStatus(PaymentStatus.PENDING.toString());
        }

        return responseBuilder.build();
    }

    private void redirectToSuccessPage(HttpServletResponse response, String txnRef, String transactionNo, String amount)
            throws IOException {
        // Convert amount from VND cents to VND (VNPay returns amount multiplied by 100)
        Double amountInVND = Double.parseDouble(amount) / 100;
        String successUrl = String.format(
                "%s/payment/success?booking=%s&transaction=%s&amount=%.0f",
                frontendUrl, txnRef, transactionNo, amountInVND);
        response.sendRedirect(successUrl);
    }

    private void redirectToFailurePage(HttpServletResponse response, String errorMessage) throws IOException {
        String encodedMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
        String failureUrl = frontendUrl + "/payment/failed?message=" + encodedMessage;
        response.sendRedirect(failureUrl);
    }

    private Long generateOrderCode() {
        return System.currentTimeMillis();
    }

    private void verifyBookingOwnership(String bookingReference, UUID userId) {
        // Extract original booking reference if this is a reschedule transaction
        final String originalBookingReference = bookingReference.contains("_RESCHEDULE_")
                ? bookingReference.split("_RESCHEDULE_")[0]
                : bookingReference;

        Booking booking = bookingRepository.findByBookingReference(originalBookingReference)
                .orElseThrow(() -> new PaymentProcessingException("Booking not found: " + originalBookingReference));

        if (!booking.getUserId().equals(userId)) {
            throw new SecurityException("You do not have permission to access this booking");
        }
    }

    @Override
    @Transactional
    public Payment completeManualPayment(String bookingReference, String transactionReference) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new PaymentProcessingException("Booking not found: " + bookingReference));

        // Find the initial payment record
        Payment payment = paymentRepository.findByBookingAndPaymentType(booking, PaymentType.BOOKING_INITIAL)
                .orElseThrow(() -> new PaymentProcessingException(
                        "Initial payment record not found for booking: " + bookingReference));

        // Update the payment to completed status
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaymentType(PaymentType.BOOKING); // Change from BOOKING_INITIAL to BOOKING
        payment.setTransactionId(transactionReference);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setDescription("Manual bank transfer for booking: " + bookingReference);

        // Keep the original payment method (should be BANK_TRANSFER_MANUAL)
        // and amount from the initial record

        payment = paymentRepository.save(payment);

        // Publish payment completion event
        eventPublisher.publishEvent(new PaymentProcessedEvent(this, bookingReference, true, transactionReference));

        return payment;
    }
}