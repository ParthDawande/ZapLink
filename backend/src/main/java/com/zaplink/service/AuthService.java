package com.zaplink.service;

import com.zaplink.dto.AuthResponse;
import com.zaplink.dto.LoginRequest;
import com.zaplink.dto.RegisterRequest;
import com.zaplink.dto.UserResponse;
import com.zaplink.exception.AccountDisabledException;
import com.zaplink.exception.EmailAlreadyExistsException;
import com.zaplink.exception.InvalidCredentialsException;
import com.zaplink.exception.UserNotFoundException;
import com.zaplink.exception.UsernameAlreadyExistsException;
import com.zaplink.model.Role;
import com.zaplink.model.User;
import com.zaplink.repository.UserRepository;
import com.zaplink.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final long expirationMs;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Value("${jwt.expiration-ms}") long expirationMs) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new UsernameAlreadyExistsException(req.username());
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException(req.email());
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user.setIsActive(true);

        User saved = userRepository.save(user);
        String token = jwtUtil.generateToken(saved.getId(), saved.getUsername(), saved.getRole());

        return new AuthResponse(
                token,
                saved.getId(),
                saved.getUsername(),
                saved.getEmail(),
                saved.getRole().name(),
                expirationMs
        );
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (!user.getIsActive()) {
            throw new AccountDisabledException();
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                expirationMs
        );
    }

    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt()
        );
    }
}
