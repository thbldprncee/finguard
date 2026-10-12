package com.finguard.audit.security;

import com.finguard.audit.config.OidcProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

import java.util.Collection;

public final class OidcTokenValidation {

    private OidcTokenValidation() {}

    /** Expiry/not-before (with 60s skew), issuer and audience. The signature is checked by the decoder. */
    public static OAuth2TokenValidator<Jwt> validator(OidcProperties properties) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience()));
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience);
    }
}
