package com.boeing.aircraftservice.jwt;

import com.boeing.aircraftservice.enums.EnumTokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class JWTToken {

    @Value("${jwt.secret}")
    private String sceretString;

    @Value("${jwt.refresh.secret}")
    private String refreshSecretString;

    @Value("${jwt.algorithms}")
    private String algorithm;

    private SecretKey getSecretKey(EnumTokenType type) {
        byte[] keyBytes = null;
        SecretKey SCRET_KEY = null;
        if(type == EnumTokenType.TOKEN) {
            keyBytes = Base64.getDecoder().decode(sceretString.getBytes(StandardCharsets.UTF_8));
            SCRET_KEY = new SecretKeySpec(keyBytes, algorithm);
            return SCRET_KEY;
        } else {
            keyBytes = Base64.getDecoder().decode(refreshSecretString.getBytes(StandardCharsets.UTF_8));
            SCRET_KEY = new SecretKeySpec(keyBytes, algorithm);
            return SCRET_KEY;
        }
    }

    public String getEmailFromJwt(String token, EnumTokenType type) {
        return getClaims(token, Claims::getSubject, type);
    }

    public String getRoleFromJwt(String token, EnumTokenType type) {
        return getClaims(token, claims -> {
            List<Map<String, String>> roles = claims.get("role", List.class);
            return roles.get(0).get("authority");
        }, type);
    }

    private <T> T getClaims(String token, Function<Claims, T> claimsTFunction, EnumTokenType type) {
        return claimsTFunction.apply(
                Jwts.parser().verifyWith(getSecretKey(type)).build().parseSignedClaims(token).getPayload());
    }


}
