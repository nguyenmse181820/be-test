package com.boeing.user.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class SmsService {

    @Value("${twilio.account.sid}")
    private String accountSid;

    @Value("${twilio.auth.token}")
    private String authToken;

    @Value("${twilio.phone.number}")
    private String fromPhoneNumber;

    public void sendOtp(String toPhoneNumber, String otp) {
           Twilio.init(accountSid, authToken);
           Message.creator(
                   new com.twilio.type.PhoneNumber(toPhoneNumber),
                   new com.twilio.type.PhoneNumber(fromPhoneNumber),
                   "Your OTP code is: " + otp
           ).create();
    }

    public String generateOtp() {
        Random random = new Random();
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }
}
