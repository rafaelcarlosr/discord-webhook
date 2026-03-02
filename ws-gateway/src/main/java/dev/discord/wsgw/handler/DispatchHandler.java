package dev.discord.wsgw.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.discord.wsgw.gateway.GatewaySession;
import dev.discord.wsgw.model.GatewayPayload;
import dev.discord.wsgw.redis.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Handles DISPATCH (op 0) payloads.
 * READY and RESUMED are handled internally; every other event type is forwarded to Redis.
 */
@Component
public class DispatchHandler {

    private static final Logger log = LoggerFactory.getLogger(DispatchHandler.class);

    private final ObjectMapper objectMapper;
    private final GatewaySession session;
    private final EventPublisher eventPublisher;

    public DispatchHandler(ObjectMapper objectMapper, GatewaySession session, EventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.session = session;
        this.eventPublisher = eventPublisher;
    }

    public Mono<Void> handle(GatewayPayload payload) {
        String eventType = payload.type();
        if (eventType == null) return Mono.empty();

        return switch (eventType) {
            case "READY" -> handleReady(payload);
            case "RESUMED" -> {
                log.info("Session resumed (seq={})", session.seq());
                yield Mono.empty();
            }
            default -> publishToRedis(eventType, payload);
        };
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> handleReady(GatewayPayload payload) {
        Map<String, Object> data = (Map<String, Object>) payload.data();
        session.sessionId((String) data.get("session_id"));
        session.resumeUrl((String) data.get("resume_gateway_url"));
        log.info("READY — session_id={} resume_url={}", session.sessionId(), session.resumeUrl());
        return Mono.empty();
    }

    private Mono<Void> publishToRedis(String eventType, GatewayPayload payload) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(payload.data()))
                .flatMap(json -> eventPublisher.publish(eventType, json))
                .doOnSuccess(id -> log.debug("Published {} → {}", eventType, id))
                .onErrorResume(e -> {
                    log.error("Failed to publish {} to Redis: {}", eventType, e.getMessage());
                    return Mono.empty();
                })
                .then();
    }
}
