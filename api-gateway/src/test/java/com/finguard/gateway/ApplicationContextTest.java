package com.finguard.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the whole application context. The other tests only start slices, so they could not
 * notice that a Spring Boot upgrade left the Spring Cloud release train incompatible: Spring
 * Cloud refuses to start then, and the gateway crash-looped in Docker while CI stayed green.
 * Also guards against the gateway properties silently not binding (no routes).
 */
@SpringBootTest(properties = {
        "OIDC_ISSUER=http://localhost/auth/realms/finguard",
        "OIDC_JWK_SET_URI=http://localhost/auth/realms/finguard/protocol/openid-connect/certs",
        "spring.kafka.listener.auto-startup=false"
})
class ApplicationContextTest {

    @Autowired
    RouteLocator routeLocator;

    @Test
    void contextStarts_andAllRoutesAreLoaded() {
        assertThat(routeLocator.getRoutes().map(Route::getId).collectList().block())
                .containsExactlyInAnyOrder("transaction-service", "fraud-service", "audit-service");
    }
}
