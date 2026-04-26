package com.finguard.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.auth.dto.AuthResponse;
import com.finguard.auth.dto.LoginRequest;
import com.finguard.auth.dto.RegisterRequest;
import com.finguard.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;

    @Test
    void register_returns201_whenRequestValid() throws Exception {
        var request = new RegisterRequest("user@example.com", "password123", null);
        var response = AuthResponse.of("token", UUID.randomUUID(), "user@example.com", "USER", 86400000L);

        when(authService.register(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void register_returns400_whenEmailInvalid() throws Exception {
        var request = new RegisterRequest("not-an-email", "password123", null);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void login_returns200_whenCredentialsValid() throws Exception {
        var request = new LoginRequest("user@example.com", "password123");
        var response = AuthResponse.of("token", UUID.randomUUID(), "user@example.com", "USER", 86400000L);

        when(authService.login(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"));
    }
}
