package com.boeing.user.service;

import com.boeing.user.dto.request.*;
import com.boeing.user.dto.response.LoginResponse;
import com.boeing.user.dto.response.TokenResponse;
import com.boeing.user.dto.response.UserDTO;
import com.boeing.user.entity.User;
import com.boeing.user.exception.BusinessLogicException;
import com.boeing.user.exception.DuplicateResourceException;
import com.boeing.user.exception.InvalidOtpException;
import com.boeing.user.exception.ResourceNotFoundException;
import com.boeing.user.mapper.UserMapper;
import com.boeing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityServiceImpl implements IdentityService {

    private static final Logger logger = LoggerFactory.getLogger(IdentityServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SmsService smsService;
    private final AuthenticationManager authenticationManager;

    private final Map<String, String> otpStorage = new HashMap<>();

    @Override
    public UserDTO registerUser(RegisterRequest request) {
        logger.info("Registering user with email: {}", request.getEmail());
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }
        userRepository.findByPhoneAndEnabledTrue(request.getPhone()).ifPresent(u -> {
            throw new DuplicateResourceException("User", "phone", request.getPhone());
        });

        try {
            User user = User.builder()
                    .email(request.getEmail())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .dob(request.getDob())
                    .phone(request.getPhone())
                    .gender(request.getGender() != null ? request.getGender().toUpperCase() : null)
                    .nationality(request.getNationality())
                    .role(request.getRole() != null ? request.getRole().toUpperCase() : "CUSTOMER")
                    .enabled(true)
                    .build();

            User savedUser = userRepository.save(user);
            logger.info("User registered successfully: {}", savedUser.getEmail());
            return UserMapper.INSTANCE.toDto(savedUser);
        } catch (Exception e) {
            logger.error("Error registering user: {}", e.getMessage(), e);
            throw new BusinessLogicException("Failed to register user: " + e.getMessage());
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        logger.info("Attempting login for user: {}", request.getUsername());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
            User user = userRepository.findByEmail(request.getUsername())
                    .orElseThrow(() -> {
                        logger.error("CRITICAL: User {} authenticated but not found in repository.", request.getUsername());
                        return new BadCredentialsException("User details not found after successful authentication.");
                    });

            String token = jwtService.generateToken(user);
            logger.info("Login successful for user: {}", user.getEmail());
            return LoginResponse.builder()
                    .token(token)
                    .username(user.getEmail())
                    .build();
        } catch (BadCredentialsException e) {
            logger.warn("Login failed for user {}: Invalid credentials", request.getUsername());
            throw e;
        } catch (DisabledException e) {
            logger.warn("Login failed for user {}: Account disabled", request.getUsername());
            throw new BadCredentialsException("User account is disabled.", e);
        } catch (LockedException e) {
            logger.warn("Login failed for user {}: Account locked", request.getUsername());
            throw new BadCredentialsException("User account is locked.", e);
        } catch (AuthenticationException e) {
            logger.warn("Login failed for user {}: {}", request.getUsername(), e.getMessage());
            throw new BadCredentialsException("Invalid email or password.", e);
        }
    }

    @Override
    public boolean resetPassword(ResetPasswordRequest request) {
        logger.info("Attempting to reset password with token");
        if (jwtService.isTokenExpired(request.getToken())) {
            logger.warn("Password reset failed: Token expired");
            throw new BusinessLogicException("Password reset token has expired.");
        }

        String userIdStr = jwtService.extractClaim(request.getToken(), claims -> claims.get("userId", String.class));
        String purpose = jwtService.extractClaim(request.getToken(), claims -> claims.get("purpose", String.class));

        if (userIdStr == null || !"RESET_PASSWORD".equals(purpose)) {
            logger.warn("Password reset failed: Invalid or missing reset token purpose/userId");
            throw new BadCredentialsException("Invalid password reset token.");
        }

        UUID userId = UUID.fromString(userIdStr);

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            logger.warn("Password reset failed for user {}: Passwords do not match", userId);
            throw new BusinessLogicException("New password and confirmation password do not match.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.error("Password reset failed: User {} not found with token.", userId);
                    return new ResourceNotFoundException("User", "id provided in token", userId.toString());
                });

        if (!user.isEnabled()) {
            logger.warn("Password reset failed: User {} account is disabled.", userId);
            throw new BusinessLogicException("Cannot reset password. User account is disabled.");
        }

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        logger.info("Password reset successfully for user: {}", user.getEmail());
        return true;
    }

    @Override
    public TokenResponse verifyOTP(VerifyOTP otpRequest) {
        String phoneNumber = otpRequest.getPhone();
        logger.info("Verifying OTP for phone: {}", phoneNumber);
        String storedOtp = otpStorage.get(phoneNumber);

        if (storedOtp == null || !storedOtp.equals(otpRequest.getOtp())) {
            if (storedOtp != null) otpStorage.remove(phoneNumber);
            logger.warn("OTP verification failed for phone {}: Invalid or expired OTP", phoneNumber);
            throw new InvalidOtpException("Invalid or expired OTP.");
        }

        otpStorage.remove(phoneNumber);
        User user = userRepository.findByPhoneAndEnabledTrue(phoneNumber)
                .orElseThrow(() -> {
                    logger.warn("OTP verification successful, but no active user found for phone {}", phoneNumber);
                    return new ResourceNotFoundException("Active User", "phone", phoneNumber);
                });

        String resetToken = jwtService.generateTokenForResetPassword(user);
        logger.info("OTP verified successfully for phone {}. Reset token generated for user {}", phoneNumber, user.getEmail());
        return TokenResponse.builder().token(resetToken).build();
    }

    @Override
    public Object forgetPasswordRequest(ForgetPasswordRequest request) {
        String phoneNumber = request.getPhone();
        logger.info("Forget password request for phone: {}", phoneNumber);

        userRepository.findByPhoneAndEnabledTrue(phoneNumber)
                .orElseThrow(() -> {
                    logger.warn("Forget password request failed: No active user found for phone {}", phoneNumber);
                    return new ResourceNotFoundException("Active User", "phone", phoneNumber);
                });

        String formattedPhone = formatPhoneNumber(phoneNumber);
        String otp = smsService.generateOtp();
        otpStorage.put(phoneNumber, otp);
        smsService.sendOtp(formattedPhone, otp);

        logger.info("Generated OTP for {}: {} (SMS sending is commented out)", phoneNumber, otp);

        return Map.of("message", "If your phone number is registered and active, an OTP will be sent.");
    }

    private String formatPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or empty.");
        }
        if (phoneNumber.startsWith("0")) {
            return "+84" + phoneNumber.substring(1);
        }
        if (!phoneNumber.startsWith("+")) {
            throw new IllegalArgumentException("Phone number must include country code.");
        }
        return phoneNumber;
    }
}