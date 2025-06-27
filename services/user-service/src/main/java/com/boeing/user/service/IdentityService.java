package com.boeing.user.service;

import com.boeing.user.dto.request.*;
import com.boeing.user.dto.response.LoginResponse;
import com.boeing.user.dto.response.TokenResponse;
import com.boeing.user.dto.response.UserDTO;

public interface IdentityService {
    UserDTO registerUser(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    boolean resetPassword(ResetPasswordRequest request);
    TokenResponse verifyOTP(VerifyOTP otp);
    Object forgetPasswordRequest(ForgetPasswordRequest request);
}
