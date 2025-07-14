package com.boeing.user.service;

import com.boeing.user.dto.request.*;
import com.boeing.user.dto.response.LoginResponse;
import com.boeing.user.dto.response.TokenResponse;
import com.boeing.user.dto.response.UserDTO;
import com.boeing.user.dto.security.Token;

public interface IdentityService {
    Token.ValidationResponse validateToken(Token.ValidationRequest request);
    UserDTO registerUser(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    boolean resetPassword(ResetPasswordRequest request);
    TokenResponse verifyOTP(VerifyOTP otp);
    Object forgetPasswordRequest(ForgetPasswordRequest request);
}
