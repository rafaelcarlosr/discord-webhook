package dev.discord.wsgw.gateway;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the mutable state required for a Discord Gateway session.
 * Thread-safe via atomic references.
 */
public class GatewaySession {

    private final AtomicInteger seq = new AtomicInteger(0);
    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private final AtomicReference<String> resumeUrl = new AtomicReference<>();

    public int seq() { return seq.get(); }

    public void updateSeq(int s) {
        if (s > 0) seq.set(s);
    }

    public String sessionId() { return sessionId.get(); }

    public void sessionId(String id) { sessionId.set(id); }

    public String resumeUrl() { return resumeUrl.get(); }

    public void resumeUrl(String url) { resumeUrl.set(url); }

    public boolean canResume() { return sessionId.get() != null; }

    public void reset() {
        sessionId.set(null);
        seq.set(0);
        resumeUrl.set(null);
    }
}
