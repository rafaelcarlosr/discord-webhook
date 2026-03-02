package dev.discord.wsgw.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.discord.wsgw.handler.PayloadRouter;
import dev.discord.wsgw.model.GatewayPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactiveWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;

/**
 * Manages a single Discord Gateway WebSocket connection.
 * A fresh send sink is created per connection so reconnects are clean.
 */
@Component
public class DiscordGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(DiscordGatewayClient.class);

    static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";

    private final ReactiveWebSocketClient wsClient;
    private final ObjectMapper objectMapper;
    private final PayloadRouter router;
    private final GatewaySession session;
    private final HeartbeatManager heartbeatManager;

    public DiscordGatewayClient(ReactiveWebSocketClient wsClient,
                                ObjectMapper objectMapper,
                                PayloadRouter router,
                                GatewaySession session,
                                HeartbeatManager heartbeatManager) {
        this.wsClient = wsClient;
        this.objectMapper = objectMapper;
        this.router = router;
        this.session = session;
        this.heartbeatManager = heartbeatManager;
    }

    public Mono<Void> connect(String url) {
        return Mono.defer(() -> {
            Sinks.Many<String> sendSink = Sinks.many().unicast().onBackpressureBuffer();

            return wsClient.execute(URI.create(url), wsSession -> {
                var inbound = wsSession.receive()
                        .map(WebSocketMessage::getPayloadAsText)
                        .flatMap(raw -> parseAndRoute(raw, sendSink))
                        .doFinally(sig -> {
                            log.info("Inbound stream ended ({}), stopping heartbeat", sig);
                            heartbeatManager.stop();
                            sendSink.tryEmitComplete();
                        })
                        .then();

                var outbound = wsSession.send(sendSink.asFlux().map(wsSession::textMessage));

                return inbound.and(outbound);
            });
        });
    }

    private Mono<Void> parseAndRoute(String raw, Sinks.Many<String> sendSink) {
        return Mono.fromCallable(() -> objectMapper.readValue(raw, GatewayPayload.class))
                .doOnNext(payload -> {
                    if (payload.seq() != null) session.updateSeq(payload.seq());
                })
                .flatMap(payload -> router.route(payload, sendSink))
                .onErrorResume(JsonProcessingException.class, e -> {
                    log.warn("Failed to parse payload: {}", e.getMessage());
                    return Mono.empty();
                });
    }
}
