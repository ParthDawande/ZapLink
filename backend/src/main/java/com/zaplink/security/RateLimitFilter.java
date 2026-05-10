package com.zaplink.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaplink.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Public paths (e.g. /{shortCode}) and Swagger UI assets bypass rate limiting entirely.
        if (!path.startsWith("/api/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")) {
            filterChain.doFilter(request, response);
            return;
        }

        String policy;
        String key;

        if ("POST".equalsIgnoreCase(method) && "/api/auth/register".equals(path)) {
            // No user yet — key by IP to block account-creation abuse.
            // In production behind a reverse proxy, switch to X-Forwarded-For parsing — see Roadmap.
            policy = "register";
            key = request.getRemoteAddr();
        } else if ("POST".equalsIgnoreCase(method) && "/api/auth/login".equals(path)) {
            // No user yet — key by IP for credential-stuffing defence.
            // In production behind a reverse proxy, switch to X-Forwarded-For parsing — see Roadmap.
            policy = "login";
            key = request.getRemoteAddr();
        } else if ("POST".equalsIgnoreCase(method) && "/api/links".equals(path)) {
            policy = "create-link";
            key = userKeyOrIp(request);
        } else {
            policy = "default";
            key = userKeyOrIp(request);
        }

        if (!rateLimitService.tryConsume(policy, key)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            ErrorResponse body = new ErrorResponse(
                    LocalDateTime.now(),
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                    "Too many requests. Please try again later.",
                    path
            );
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String userKeyOrIp(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser principal) {
            return String.valueOf(principal.userId());
        }
        return request.getRemoteAddr();
    }
}
