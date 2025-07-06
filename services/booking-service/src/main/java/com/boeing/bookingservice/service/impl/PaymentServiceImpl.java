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
import com.boeing.bookingservice.utils.VNPayUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
public class PaymentServiceImpl implements PaymentService {
    private final VNPAYConfig vnPayConfig;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${frontend.url}")
    private String frontendUrl;

    public PaymentServiceImpl(VNPAYConfig vnPayConfig,
                              PaymentRepository paymentRepository,
                              BookingRepository bookingRepository,
                              @Lazy BookingService bookingService,
                              ApplicationEventPublisher eventPublisher) {
        this.vnPayConfig = vnPayConfig;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Payment createPayment(CreatePaymentRequest request, UUID userId) {
        // Verify user owns the booking
        verifyBookingOwnership(request.getBookingReference(), userId);
        
        Booking booking = bookingRepository.findByBookingReference(request.getBookingReference())
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with reference: " + request.getBookingReference()));
        
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
        // Verify user owns the booking
        verifyBookingOwnership(request.getBookingReference(), userId);

        PaymentDTO.VNPayResponse response = requestPayment(
                request.getAmount(),
                request.getBankCode(),
                request.getBookingReference(),
                null
        );
        return response.getPaymentUrl();
    }

    public PaymentDTO.VNPayResponse requestPayment(
            Double amount,
            String bankCode,
            String bookingReference,
            HttpServletRequest request
    ) {
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
        String queryUrl = VNPayUtil.getPaymentURL(vnpParamsMap, true);
        String hashData = VNPayUtil.getPaymentURL(vnpParamsMap, false);
        String vnpSecureHash = VNPayUtil.hmacSHA512(vnPayConfig.getSecretKey(), hashData);
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;
        String paymentUrl = vnPayConfig.getVnp_PayUrl() + "?" + queryUrl;

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
        try {
            if (!validateCallbackSignature(request)) {
                eventPublisher.publishEvent(new PaymentProcessedEvent(this, txnRef, false, "Invalid payment signature"));
                redirectToFailurePage(response, "Invalid payment signature");
                return;
            }

            String responseCode = request.getParameter("vnp_ResponseCode");

            if ("00".equals(responseCode)) {
                handleSuccessfulPayment(txnRef, transactionNo, amount, request);
                eventPublisher.publishEvent(new PaymentProcessedEvent(this, txnRef, true, transactionNo));
                redirectToSuccessPage(response, txnRef, transactionNo, amount);
            } else {
                eventPublisher.publishEvent(new PaymentProcessedEvent(this, txnRef, false, "Payment failed with code: " + responseCode));
                redirectToFailurePage(response, "Payment failed with code: " + responseCode);
            }

        } catch (Exception e) {
            eventPublisher.publishEvent(new PaymentProcessedEvent(this, txnRef, false, "Error processing payment"));
            redirectToFailurePage(response, "Error processing payment");
        }
    }

    @Transactional
    protected void handleSuccessfulPayment(String txnRef, String transactionNo, String amount, HttpServletRequest request) {
        Booking booking = bookingRepository.findByBookingReference(txnRef)
                .orElseThrow(() -> new PaymentProcessingException("Booking not found: " + txnRef));

        Optional<Payment> existingPayment = paymentRepository.findByVnpTxnRef(txnRef);

        Payment payment;
        if (existingPayment.isPresent()) {
            payment = existingPayment.get();

            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setVnpResponseCode(request.getParameter("vnp_ResponseCode"));
            payment.setVnpTransactionNo(transactionNo);
            payment.setTransactionId(transactionNo);
            payment.setPaymentDate(LocalDateTime.now());
            payment.setPaymentMethod(PaymentMethod.VNPAY_BANKTRANSFER);

            Double callbackAmount = Double.parseDouble(amount);
            payment.setAmount(callbackAmount);

        } else {
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

    private void redirectToSuccessPage(HttpServletResponse response, String txnRef, String transactionNo, String amount) throws IOException {
        String successUrl = String.format(
                "%s/payment/success?booking=%s&transaction=%s&amount=%s",
                frontendUrl, txnRef, transactionNo, amount
        );
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

        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(() -> new PaymentProcessingException("Booking not found: " + bookingReference));

        if (!booking.getUserId().equals(userId)) {
            throw new SecurityException("You do not have permission to access this booking");
        }
    }
}