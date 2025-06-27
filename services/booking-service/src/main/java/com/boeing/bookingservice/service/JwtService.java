package com.boeing.bookingservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    @Value("${jwt.secret.key}")
    private String jwtSecretKeyString;
    private Key jwtSigningKey;

    @PostConstruct
    public void init() {
        if (jwtSecretKeyString == null || jwtSecretKeyString.isBlank()) {
            log.error("JWT secret key is not configured!");
            throw new IllegalArgumentException("JWT secret key must be configured.");
        }
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecretKeyString);
        this.jwtSigningKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT signing key initialized for HS256/HS512.");
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parserBuilder().setSigningKey(jwtSigningKey).build().parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty or null: {}", e.getMessage());
            throw e;
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractClaim(token, Claims::getExpiration).before(new java.util.Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public UUID extractUserIdAsUUID(String token) {
        String userIdStr = extractClaim(token, claims -> claims.get("userId", String.class));
        if (userIdStr != null) {
            try {
                return UUID.fromString(userIdStr);
            } catch (IllegalArgumentException e) {
                log.error("Invalid UUID format for userId claim in token: {}", userIdStr);
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<GrantedAuthority> extractAuthorities(String token) {
        Claims claims = extractAllClaims(token);

        String roleClaim = claims.get("role", String.class);

        if (roleClaim == null || roleClaim.isBlank()) {
            log.warn("Role claim is missing or blank in JWT for token: {}", token.substring(0, Math.min(token.length(), 20)) + "...");
            return List.of();
        }

        log.debug("Extracted role claim: {}", roleClaim);
        return List.of(new SimpleGrantedAuthority(roleClaim.toUpperCase()));
    }

    public String extractFirstName(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("firstName", String.class);
    }

    public String extractLastName(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("lastName", String.class);
    }
}