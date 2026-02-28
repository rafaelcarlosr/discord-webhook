package dev.discord.gateway.filter;

import dev.discord.gateway.crypto.Ed25519Verifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that verifies Discord Ed25519 request signatures before the
 * request reaches the controller.  Registered via {@code FilterRegistrationBean}
 * and mapped only to the interactions endpoint.
 */
public class SignatureVerificationFilter extends OncePerRequestFilter {

    private final Ed25519Verifier verifier;

    public SignatureVerificationFilter(Ed25519Verifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String signature = request.getHeader("X-Signature-Ed25519");
        String timestamp = request.getHeader("X-Signature-Timestamp");

        if (signature == null || timestamp == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing signature headers");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        byte[] body = cachedRequest.getCachedBody();

        if (!verifier.verify(signature, timestamp, body)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
            return;
        }

        chain.doFilter(cachedRequest, response);
    }
}
