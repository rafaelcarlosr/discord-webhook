package dev.discord.wsgw.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.discord.wsgw.config.DiscordProperties;
import dev.discord.wsgw.gateway.GatewaySession;
import dev.discord.wsgw.gateway.HeartbeatManager;
import dev.discord.wsgw.model.GatewayPayload;
import dev.discord.wsgw.model.Opcode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Map;

/**
 * Routes incoming Gateway payloads by opcode to the appropriate handler.
 */
@Component
public class PayloadRouter {

    private static final Logger log = LoggerFactory.getLogger(PayloadRouter.class);

    private final ObjectMapper objectMapper;
    private final GatewaySession session;
    private final HeartbeatManager heartbeatManager;
    private final DiscordProperties discord;
    private final DispatchHandler dispatchHandler;

    public PayloadRouter(ObjectMapper objectMapper,
                         GatewaySession session,
                         HeartbeatManager heartbeatManager,
                         DiscordProperties discord,
                         DispatchHandler dispatchHandler) {
        this.objectMapper = objectMapper;
        this.session = session;
        this.heartbeatManager = heartbeatManager;
        this.discord = discord;
        this.dispatchHandler = dispatchHandler;
    }

    public Mono<Void> route(GatewayPayload payload, Sinks.Many<String> sendSink) {
        Opcode opcode;
        try {
            opcode = Opcode.of(payload.op());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown opcode {}, ignoring", payload.op());
            return Mono.empty();
        }

        return switch (opcode) {
            case DISPATCH -> dispatchHandler.handle(payload);
            case HELLO -> handleHello(payload, sendSink);
            case HEARTBEAT_ACK -> {
                heartbeatManager.ack();
                yield Mono.empty();
            }
            case HEARTBEAT -> {
                // Discord requested a heartbeat immediately
                sendSink.tryEmitNext(buildHeartbeat());
                yield Mono.empty();
            }
            case RECONNECT -> {
                log.info("RECONNECT received — forcing reconnect");
                yield Mono.error(new IllegalStateException("RECONNECT requested by Discord"));
            }
            case INVALID_SESSION -> handleInvalidSession(payload, sendSink);
            default -> {
                log.debug("Unhandled opcode: {}", opcode);
                yield Mono.empty();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> handleHello(GatewayPayload payload, Sinks.Many<String> sendSink) {
        Map<String, Object> data = (Map<String, Object>) payload.data();
        int intervalMs = data.get("heartbeat_interval") instanceof Number n ? n.intValue() : 41250;
        heartbeatManager.start(intervalMs, sendSink);
        sendSink.tryEmitNext(session.canResume() ? buildResume() : buildIdentify());
        return Mono.empty();
    }

    private Mono<Void> handleInvalidSession(GatewayPayload payload, Sinks.Many<String> sendSink) {
        boolean resumable = Boolean.TRUE.equals(payload.data());
        if (!resumable) {
            log.info("INVALID_SESSION (not resumable) — re-identifying");
            session.reset();
        } else {
            log.info("INVALID_SESSION (resumable) — will resume");
        }
        sendSink.tryEmitNext(session.canResume() ? buildResume() : buildIdentify());
        return Mono.empty();
    }

    private String buildHeartbeat() {
        ObjectNode node = objectMapper.createObjectNode().put("op", 1);
        int seq = session.seq();
        if (seq == 0) node.putNull("d");
        else node.put("d", seq);
        return node.toString();
    }

    private String buildIdentify() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "op", 2,
                    "d", Map.of(
                            "token", discord.botToken(),
                            "intents", discord.intents(),
                            "shard", new int[]{discord.shard().id(), discord.shard().total()},
                            "properties", Map.of(
                                    "os", "linux",
                                    "browser", "discord-ws-gateway",
                                    "device", "discord-ws-gateway"
                            )
                    )
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build IDENTIFY payload", e);
        }
    }

    private String buildResume() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "op", 6,
                    "d", Map.of(
                            "token", discord.botToken(),
                            "session_id", session.sessionId(),
                            "seq", session.seq()
                    )
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build RESUME payload", e);
        }
    }
}
