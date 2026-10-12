package com.finguard.transaction.security;

/**
 * Request facts the fraud engine relies on. They are taken from the connection and headers,
 * not from the JSON body, so a client cannot simply put "safe" values there.
 *
 * @param clientIp X-Client-Ip, set by the API gateway from the TCP connection (client value stripped)
 * @param deviceId X-Device-Id, a client-generated identifier; still client-controlled, which is
 *                 why the fraud engine treats a missing value as risky instead of neutral
 */
public record RequestMetadata(String idempotencyKey, String correlationId, String clientIp, String deviceId) {}
