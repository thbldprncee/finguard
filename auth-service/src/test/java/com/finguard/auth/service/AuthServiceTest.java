package com.finguard.auth.service;

import com.finguard.auth.dto.LoginRequest;
import com.finguard.auth.dto.RegisterRequest;
import com.finguard.auth.entity.User;
import com.finguard.auth.kafka.AuditEventProducer;
import com.finguard.auth.repository.UserRepository;
import com.finguard.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock AuthenticationManager authenticationManager;
    @Mock AuditEventProducer auditEventProducer;

    @InjectMocks AuthService authService;

    @Test
    void register_createsUser_whenEmailNotTaken() {
        var request = new RegisterRequest("user@example.com", "password123", "USER");
        var savedUser = User.builder()
                .id(UUID.randomUUID()).email("user@example.com")
                .passwordHash("hashed").role(User.Role.USER).build();

        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(savedUser);
        when(jwtService.generateToken(any(), any(), any())).thenReturn("token123");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        var response = authService.register(request, "corr-1");

        assertThat(response.token()).isEqualTo("token123");
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.role()).isEqualTo("USER");
        verify(userRepository).save(any());
    }

    @Test
    void register_throwsException_whenEmailAlreadyExists() {
        var request = new RegisterRequest("existing@example.com", "password123", null);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void login_returnsToken_whenCredentialsValid() {
        var request = new LoginRequest("user@example.com", "password123");
        var user = User.builder()
                .id(UUID.randomUUID()).email("user@example.com")
                .passwordHash("hashed").role(User.Role.ANALYST).build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(any(), any(), any())).thenReturn("jwt-token");
        when(jwtService.getExpirationMs()).thenReturn(86400000L);

        var response = authService.login(request, null);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo("ANALYST");
    }
}
