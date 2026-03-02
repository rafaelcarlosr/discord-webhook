package dev.discord.wsgw.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

/**
 * Owns the connection lifecycle: starts the Discord WebSocket connection on boot
 * and reconnects with exponential backoff on failure.
 *
 * <p>On each reconnect attempt the resume URL is re-evaluated: if a valid session
 * exists we try to RESUME at Discord's provided URL, otherwise we fall back to
 * a fresh IDENTIFY at the standard gateway URL.
 */
@Component
public class ReconnectManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReconnectManager.class);

    private final DiscordGatewayClient gatewayClient;
    private final GatewaySession session;

    public ReconnectManager(DiscordGatewayClient gatewayClient, GatewaySession session) {
        this.gatewayClient = gatewayClient;
        this.session = session;
    }

    @Override
    public void run(ApplicationArguments args) {
        connectWithRetry().block();
    }

    private Mono<Void> connectWithRetry() {
        return Mono.defer(this::resolveUrlAndConnect)
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(signal -> log.warn(
                                "Reconnecting (attempt {}): {}",
                                signal.totalRetries() + 1,
                                signal.failure().getMessage())));
    }

    private Mono<Void> resolveUrlAndConnect() {
        String url = (session.canResume() && session.resumeUrl() != null)
                ? session.resumeUrl()
                : DiscordGatewayClient.GATEWAY_URL;
        log.info("Connecting to {}", url);
        return gatewayClient.connect(url);
    }
}
