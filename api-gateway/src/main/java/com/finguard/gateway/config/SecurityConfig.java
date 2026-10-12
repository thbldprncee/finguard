package com.finguard.gateway.config;

import com.finguard.gateway.security.KeycloakJwtConverter;
import com.finguard.gateway.security.OidcTokenValidation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;

/**
 * Token validation runs in the Spring Security filter chain, so it covers everything the gateway
 * serves: proxied routes and its own endpoints (the SSE feed). A Spring Cloud Gateway GlobalFilter
 * only runs for proxied routes, which is how /api/sse/events used to be open.
 * Login, registration and token refresh are Keycloak's job (nginx serves it under /auth).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtDecoder jwtDecoder) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .anyExchange().hasAnyRole("USER", "ANALYST", "ADMIN"))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtDecoder(jwtDecoder)
                        .jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(new KeycloakJwtConverter()))))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(OidcProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(OidcTokenValidation.validator(properties));
        return decoder;
    }
}
