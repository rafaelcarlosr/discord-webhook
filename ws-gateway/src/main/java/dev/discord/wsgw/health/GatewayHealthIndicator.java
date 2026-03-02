package dev.discord.wsgw.health;

import dev.discord.wsgw.gateway.GatewaySession;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Exposes Discord Gateway session state via {@code /actuator/health}.
 * DOWN when no session has been established (pre-READY or after unresumable disconnect).
 */
@Component("gateway")
public class GatewayHealthIndicator implements ReactiveHealthIndicator {

    private final GatewaySession session;

    public GatewayHealthIndicator(GatewaySession session) {
        this.session = session;
    }

    @Override
    public Mono<Health> health() {
        return Mono.fromCallable(() -> session.canResume()
                ? Health.up()
                        .withDetail("sessionId", session.sessionId())
                        .withDetail("seq", session.seq())
                        .build()
                : Health.down()
                        .withDetail("reason", "No active session")
                        .build());
    }
}
