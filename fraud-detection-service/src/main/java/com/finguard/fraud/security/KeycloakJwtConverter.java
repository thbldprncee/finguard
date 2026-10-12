package com.finguard.fraud.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps a Keycloak access token to an {@link AuthenticatedUser}: "sub" is the user id,
 * realm roles (realm_access.roles) become ROLE_* authorities. Only FinGuard roles count.
 * A valid token without any of them is authenticated but has no authorities, so requests get
 * 403, not 401: a 401 would make the SPA re-login, and the live SSO session would send the user
 * straight back - an endless redirect loop.
 */
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Most privileged first: the first match becomes AuthenticatedUser.role. */
    static final List<String> ROLES = List.of("ADMIN", "ANALYST", "USER");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<String> roles = realmRoles(jwt);
        String primaryRole = ROLES.stream().filter(roles::contains).findFirst().orElse(null);

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidBearerTokenException("Token subject is not a user id");
        }

        var authorities = ROLES.stream().filter(roles::contains)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        var user = new AuthenticatedUser(userId, jwt.getClaimAsString("email"), primaryRole);
        return new UsernamePasswordAuthenticationToken(user, jwt, authorities);
    }

    static Set<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return Set.of();
        }
        return roles.stream().map(String::valueOf).collect(Collectors.toSet());
    }
}
