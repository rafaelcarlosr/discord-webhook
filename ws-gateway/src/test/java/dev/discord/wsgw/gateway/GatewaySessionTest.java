package dev.discord.wsgw.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySessionTest {

    @Test
    void initialStateIsEmpty() {
        var session = new GatewaySession();
        assertThat(session.seq()).isZero();
        assertThat(session.sessionId()).isNull();
        assertThat(session.resumeUrl()).isNull();
        assertThat(session.canResume()).isFalse();
    }

    @Test
    void updateSeqIgnoresNonPositiveValues() {
        var session = new GatewaySession();
        session.updateSeq(0);
        assertThat(session.seq()).isZero();
        session.updateSeq(-5);
        assertThat(session.seq()).isZero();
    }

    @Test
    void updateSeqAcceptsPositiveValues() {
        var session = new GatewaySession();
        session.updateSeq(42);
        assertThat(session.seq()).isEqualTo(42);
        session.updateSeq(100);
        assertThat(session.seq()).isEqualTo(100);
    }

    @Test
    void canResumeOnceSessionIdIsSet() {
        var session = new GatewaySession();
        session.sessionId("abc-123");
        assertThat(session.canResume()).isTrue();
        assertThat(session.sessionId()).isEqualTo("abc-123");
    }

    @Test
    void resetClearsAllState() {
        var session = new GatewaySession();
        session.sessionId("abc-123");
        session.resumeUrl("wss://us-east1.discord.gg");
        session.updateSeq(100);

        session.reset();

        assertThat(session.canResume()).isFalse();
        assertThat(session.seq()).isZero();
        assertThat(session.resumeUrl()).isNull();
    }
}
