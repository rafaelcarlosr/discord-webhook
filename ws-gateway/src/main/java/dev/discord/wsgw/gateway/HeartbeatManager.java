package dev.discord.wsgw.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the Discord Gateway heartbeat cycle.
 *
 * <p>On start, schedules the first heartbeat after a random jitter in [0, interval),
 * then every {@code interval} ms thereafter — per the Discord Gateway spec.
 *
 * <p>If a HEARTBEAT_ACK is not received before the next heartbeat tick, the connection
 * is considered a zombie and an error is emitted on the send sink to force reconnection.
 */
@Component
public class HeartbeatManager {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatManager.class);

    private final ObjectMapper objectMapper;
    private final GatewaySession session;

    private volatile Disposable heartbeatDisposable;
    private volatile Sinks.Many<String> sendSink;
    private final AtomicBoolean ackReceived = new AtomicBoolean(true);

    public HeartbeatManager(ObjectMapper objectMapper, GatewaySession session) {
        this.objectMapper = objectMapper;
        this.session = session;
    }

    public void start(int intervalMs, Sinks.Many<String> sink) {
        this.sendSink = sink;
        ackReceived.set(true);
        stop();

        long jitter = (long) (intervalMs * Math.random());
        heartbeatDisposable = Flux.concat(
                Flux.just(0L).delaySubscription(Duration.ofMillis(jitter)),
                Flux.interval(Duration.ofMillis(intervalMs))
        ).subscribe(tick -> {
            if (!ackReceived.compareAndSet(true, false)) {
                log.warn("Heartbeat ACK not received — zombie connection, forcing reconnect");
                stop();
                sink.tryEmitError(new IllegalStateException("Heartbeat ACK timeout"));
                return;
            }
            sendHeartbeat(sink);
        });

        log.info("Heartbeat started (interval={}ms, jitter={}ms)", intervalMs, jitter);
    }

    public void ack() {
        ackReceived.set(true);
        log.debug("Heartbeat ACK received");
    }

    public void stop() {
        if (heartbeatDisposable != null && !heartbeatDisposable.isDisposed()) {
            heartbeatDisposable.dispose();
            log.debug("Heartbeat stopped");
        }
    }

    private void sendHeartbeat(Sinks.Many<String> sink) {
        ObjectNode node = objectMapper.createObjectNode().put("op", 1);
        int seq = session.seq();
        if (seq == 0) node.putNull("d");
        else node.put("d", seq);
        sink.tryEmitNext(node.toString());
        log.debug("Heartbeat sent (seq={})", seq == 0 ? "null" : seq);
    }
}
