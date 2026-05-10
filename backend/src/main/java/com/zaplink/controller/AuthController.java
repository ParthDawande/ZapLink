package com.zaplink.controller;

import com.zaplink.dto.AuthResponse;
import com.zaplink.dto.LoginRequest;
import com.zaplink.dto.RegisterRequest;
import com.zaplink.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(
        summary = "Register a new user account",
        description = "Creates a new user account. Returns a JWT token and user details on success (201). " +
                      "Returns 409 if the username or email is already taken."
    )
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @Operation(
        summary = "Log in and receive a JWT token",
        description = "Authenticates using email and password. Returns a JWT Bearer token valid for 24 hours. " +
                      "Returns 401 for invalid credentials and 403 if the account has been banned."
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
