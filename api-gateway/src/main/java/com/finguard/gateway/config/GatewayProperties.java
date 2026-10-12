package com.finguard.gateway.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param trustProxyHeaders take the client IP from X-Real-IP. Only enable this when the gateway
 *                          is reachable exclusively through a proxy that overwrites the header
 *                          (the frontend nginx does); otherwise clients could pick their own IP.
 * @param sseHeartbeat      comment frames that keep idle SSE connections open through proxies
 */
@Validated
@ConfigurationProperties("finguard.gateway")
public record GatewayProperties(
        @DefaultValue("false") boolean trustProxyHeaders,
        @NotNull @DefaultValue("15s") Duration sseHeartbeat
) {}
