package com.finguard.transaction.security;

import com.finguard.transaction.config.OidcProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Token checks as configured in SecurityConfig, with a local RSA key instead of Keycloak's JWKS. */
class KeycloakTokenTest {

    private static final String ISSUER = "http://localhost:3000/auth/realms/finguard";
    private static final KeyPair KEYS = generate();

    private final NimbusJwtDecoder decoder = decoder();
    private final KeycloakJwtConverter converter = new KeycloakJwtConverter();

    private static KeyPair generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static NimbusJwtDecoder decoder() {
        NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
        d.setJwtValidator(OidcTokenValidation.validator(new OidcProperties(ISSUER, "unused", "finguard-api")));
        return d;
    }

    private static JWTClaimsSet.Builder claims(String sub, List<String> roles) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(sub)
                .audience(List.of("finguard-api", "account"))
                .claim("email", "user@example.com")
                .claim("realm_access", Map.of("roles", roles))
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 300_000));
    }

    private static String rs256(JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(KEYS.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void validToken_mapsSubjectAndMostPrivilegedRole() throws Exception {
        UUID userId = UUID.randomUUID();
        Jwt jwt = decoder.decode(rs256(claims(userId.toString(), List.of("default-roles-finguard", "USER", "ANALYST")).build()));

        var auth = converter.convert(jwt);
        var user = (AuthenticatedUser) auth.getPrincipal();
        assertThat(user.userId()).isEqualTo(userId);
        assertThat(user.role()).isEqualTo("ANALYST");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_ANALYST", "ROLE_USER");
    }

    @Test
    void wrongIssuer_isRejected() throws Exception {
        String token = rs256(claims(UUID.randomUUID().toString(), List.of("USER")).issuer("http://evil/realms/finguard").build());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenForAnotherClient_isRejected() throws Exception {
        String token = rs256(claims(UUID.randomUUID().toString(), List.of("ADMIN")).audience("account").build());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredToken_isRejected() throws Exception {
        String token = rs256(claims(UUID.randomUUID().toString(), List.of("USER"))
                .expirationTime(new Date(System.currentTimeMillis() - 120_000)).build());
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void hmacSignedToken_isRejected_evenWithValidClaims() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims(UUID.randomUUID().toString(), List.of("ADMIN")).build());
        jwt.sign(new MACSigner("an-attacker-chosen-secret-that-is-long-enough!!"));
        assertThatThrownBy(() -> decoder.decode(jwt.serialize())).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithoutFinGuardRole_hasNoAuthorities() throws Exception {
        Jwt jwt = decoder.decode(rs256(claims(UUID.randomUUID().toString(), List.of("offline_access")).build()));
        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }
}
