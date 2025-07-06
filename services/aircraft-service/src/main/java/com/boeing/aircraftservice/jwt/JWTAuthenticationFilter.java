package com.boeing.aircraftservice.jwt;

import com.boeing.aircraftservice.enums.EnumTokenType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class JWTAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JWTToken jwtToken;

    private final List<String> NON_USER = List.of(
            "/air-craft/api/v1/public/**", 
            "/air-craft/api/v1/aircraft/**", 
            "/air-craft/api/v1//aircraft-type/**",
            "/air-craft/swagger-ui/**",
            "/air-craft/swagger-ui/index.html",
            "/air-craft/v3/api-docs/**",
            "/air-craft/actuator/**"
    );    public String getToken(HttpServletRequest request) {
        String s = request.getHeader("Authorization");
        if (s != null && s.startsWith("Bearer ") && StringUtils.hasText(s)) {
            return s.substring(7);
        }
        return null;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            if (request.getRequestURI() != null && request.getRequestURI().contains("public")) {
                filterChain.doFilter(request, response);
                return;
            }

            if (isAuthentication(request.getRequestURI())) {
                filterChain.doFilter(request, response);
                return;
            }
            String bearerToken = getToken(request);

            String role = jwtToken.getRoleFromJwt(bearerToken, EnumTokenType.TOKEN);

            SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority(role);
            List<GrantedAuthority> roles = new ArrayList<>();
            roles.add(simpleGrantedAuthority);

            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                    new UsernamePasswordAuthenticationToken(null, null, roles);
            usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);

        } catch (Exception e) {
            log.error("Fail on set user authentication: {}", e.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthentication(String uri) {
        AntPathMatcher pathcMatcher = new AntPathMatcher();
        return NON_USER.stream().anyMatch(pattern -> pathcMatcher.match(pattern, uri));
    }

}
