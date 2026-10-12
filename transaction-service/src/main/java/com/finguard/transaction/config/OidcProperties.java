package com.finguard.transaction.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Access tokens are issued by Keycloak and signed with RS256; the service only holds
 * public keys, so a compromised service cannot mint tokens (unlike a shared HMAC secret).
 *
 * @param issuer     expected "iss": the public Keycloak realm URL the browser uses
 * @param jwkSetUri  where to fetch the realm's public keys (internal network address)
 * @param audience   required in "aud", so tokens Keycloak issued for other clients are rejected
 */
@Validated
@ConfigurationProperties("finguard.security.oidc")
public record OidcProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank @DefaultValue("finguard-api") String audience
) {}
