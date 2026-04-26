package com.finguard.auth.service;

import com.finguard.auth.dto.AuthResponse;
import com.finguard.auth.dto.LoginRequest;
import com.finguard.auth.dto.RegisterRequest;
import com.finguard.auth.entity.User;
import com.finguard.auth.kafka.AuditEventProducer;
import com.finguard.auth.repository.UserRepository;
import com.finguard.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditEventProducer auditEventProducer;

    @Transactional
    public AuthResponse register(RegisterRequest request, String correlationId) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered: " + request.email());
        }

        User.Role role = parseRole(request.role());

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(role)
                .build();

        user = userRepository.save(user);
        log.info("Registered new user: id={}, role={}", user.getId(), user.getRole());

        auditEventProducer.publishAuditEvent(
                "USER_REGISTERED",
                user.getId().toString(),
                Map.of("email", user.getEmail(), "role", user.getRole().name()),
                correlationId
        );

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getRole().name(), jwtService.getExpirationMs());
    }

    public AuthResponse login(LoginRequest request, String correlationId) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        log.info("User logged in: id={}", user.getId());

        auditEventProducer.publishAuditEvent(
                "USER_LOGIN",
                user.getId().toString(),
                Map.of("email", user.getEmail()),
                correlationId
        );

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.of(token, user.getId(), user.getEmail(), user.getRole().name(), jwtService.getExpirationMs());
    }

    private User.Role parseRole(String role) {
        if (role == null || role.isBlank()) {
            return User.Role.USER;
        }
        try {
            return User.Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return User.Role.USER;
        }
    }
}
