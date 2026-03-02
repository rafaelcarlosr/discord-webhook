package dev.discord.wsgw.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.discord.wsgw.config.DiscordProperties;
import dev.discord.wsgw.gateway.GatewaySession;
import dev.discord.wsgw.gateway.HeartbeatManager;
import dev.discord.wsgw.model.GatewayPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayloadRouterTest {

    @Mock private HeartbeatManager heartbeatManager;
    @Mock private DispatchHandler dispatchHandler;

    private GatewaySession session;
    private PayloadRouter router;
    private Sinks.Many<String> sendSink;

    @BeforeEach
    void setUp() {
        session = new GatewaySession();
        var discord = new DiscordProperties("Bot token", 513, new DiscordProperties.Shard(0, 1));
        router = new PayloadRouter(new ObjectMapper(), session, heartbeatManager, discord, dispatchHandler);
        sendSink = Sinks.many().unicast().onBackpressureBuffer();
    }

    @Test
    void heartbeatAckDelegatesToHeartbeatManager() {
        var payload = new GatewayPayload(11, null, null, null);
        StepVerifier.create(router.route(payload, sendSink)).verifyComplete();
        verify(heartbeatManager).ack();
    }

    @Test
    void reconnectEmitsError() {
        var payload = new GatewayPayload(7, null, null, null);
        StepVerifier.create(router.route(payload, sendSink))
                .verifyErrorMessage("RECONNECT requested by Discord");
    }

    @Test
    void dispatchDelegatesToDispatchHandler() {
        when(dispatchHandler.handle(any())).thenReturn(Mono.empty());
        var payload = new GatewayPayload(0, null, 5, "MESSAGE_CREATE");
        StepVerifier.create(router.route(payload, sendSink)).verifyComplete();
        verify(dispatchHandler).handle(payload);
    }

    @Test
    void unknownOpcodeIsIgnoredGracefully() {
        var payload = new GatewayPayload(99, null, null, null);
        StepVerifier.create(router.route(payload, sendSink)).verifyComplete();
        verifyNoInteractions(heartbeatManager, dispatchHandler);
    }

    @Test
    void helloStartsHeartbeatAndEmitsIdentify() {
        var data = Map.of("heartbeat_interval", 41250);
        var payload = new GatewayPayload(10, data, null, null);

        StepVerifier.create(router.route(payload, sendSink)).verifyComplete();

        verify(heartbeatManager).start(eq(41250), eq(sendSink));

        // IDENTIFY should have been enqueued on the sink
        String identify = sendSink.asFlux().blockFirst();
        assertThat(identify).contains("\"op\":2");
        assertThat(identify).contains("\"token\"");
        assertThat(identify).contains("\"shard\"");
    }

    @Test
    void helloEmitsResumeWhenSessionIsActive() {
        session.sessionId("session-xyz");
        var data = Map.of("heartbeat_interval", 41250);
        var payload = new GatewayPayload(10, data, null, null);

        StepVerifier.create(router.route(payload, sendSink)).verifyComplete();

        String resume = sendSink.asFlux().blockFirst();
        assertThat(resume).contains("\"op\":6");
        assertThat(resume).contains("session-xyz");
    }

    @Test
    void invalidSessionNonResumableResetsSession() {
        session.sessionId("old-session");
        var payload = new GatewayPayload(9, false, null, null);

        StepVerifier.create(router.route(payload, sendSink)).verifyComplete();

        assertThat(session.canResume()).isFalse();
    }
}
