package com.boeing.loyalty.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("CallToPrintStackTrace")
public class AuthenticationFilter extends OncePerRequestFilter {

    private final RestTemplate restTemplate;
    private final AntPathMatcher antPathMatcher;
    private final String authenticationUrl;
    private final ObjectMapper objectMapper;

    private final List<String> PUBLIC_PATHS = List.of(
            "/swagger-ui/**",
            "/api-docs/**",
            "/**"
    );

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // SWAGGER redirection
            if (request.getServletPath().equals("/")) {
                response.sendRedirect(request.getContextPath() + "/swagger-ui/index.html");
                return;
            }
            // PUBLIC path checking
            final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (isPublicPath(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new RuntimeException("Missing or invalid token");
            }
            String token = authHeader.substring(7);
            Token.ValidationResponse validationResponse = restTemplate.postForObject(
                    authenticationUrl,
                    Token.ValidationRequest.builder().token(token).build(),
                    Token.ValidationResponse.class
            );
            if (validationResponse == null || !validationResponse.valid()) {
                throw new RuntimeException("Invalid token");
            }
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    validationResponse.email(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + validationResponse.role()))
            );
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        } catch (Exception e) {
            e.printStackTrace();
            buildResponse(response, "Unauthorized: " + e.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void buildResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> apiResponse = Map.of(
                "statusCode", HttpStatus.UNAUTHORIZED.value(),
                "error", message
        );

        String jsonResponse = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }

    private boolean isPublicPath(HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();

        String pathToMatch;
        if (contextPath != null && !contextPath.isEmpty() && requestURI.startsWith(contextPath)) {
            pathToMatch = requestURI.substring(contextPath.length());
        } else {
            pathToMatch = requestURI;
        }

        return PUBLIC_PATHS.stream().anyMatch(pattern -> antPathMatcher.match(pattern, pathToMatch));
    }
}

