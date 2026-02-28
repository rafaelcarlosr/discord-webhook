package dev.discord.gateway.filter;

import dev.discord.gateway.crypto.Ed25519Verifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SignatureVerificationFilterTest {

    @Test
    void missingBothHeadersReturns401() throws Exception {
        Ed25519Verifier verifier = mock(Ed25519Verifier.class);
        SignatureVerificationFilter filter = new SignatureVerificationFilter(verifier);

        MockHttpServletRequest request = postInteractions("{}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void missingTimestampHeaderReturns401() throws Exception {
        Ed25519Verifier verifier = mock(Ed25519Verifier.class);
        SignatureVerificationFilter filter = new SignatureVerificationFilter(verifier);

        MockHttpServletRequest request = postInteractions("{}");
        request.addHeader("X-Signature-Ed25519", "aabb");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void invalidSignatureReturns401() throws Exception {
        Ed25519Verifier verifier = mock(Ed25519Verifier.class);
        when(verifier.verify(anyString(), anyString(), any(byte[].class))).thenReturn(false);

        SignatureVerificationFilter filter = new SignatureVerificationFilter(verifier);

        MockHttpServletRequest request = postInteractions("{\"type\":2}");
        request.addHeader("X-Signature-Ed25519", "badsig");
        request.addHeader("X-Signature-Timestamp", "12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verifyNoInteractions(chain);
    }

    @Test
    void validSignatureContinuesFilterChain() throws Exception {
        Ed25519Verifier verifier = mock(Ed25519Verifier.class);
        when(verifier.verify(anyString(), anyString(), any(byte[].class))).thenReturn(true);

        SignatureVerificationFilter filter = new SignatureVerificationFilter(verifier);

        MockHttpServletRequest request = postInteractions("{\"type\":2}");
        request.addHeader("X-Signature-Ed25519", "goodsig");
        request.addHeader("X-Signature-Timestamp", "12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verify(chain).doFilter(any(CachedBodyHttpServletRequest.class), eq(response));
    }

    @Test
    void verifierReceivesCorrectArguments() throws Exception {
        Ed25519Verifier verifier = mock(Ed25519Verifier.class);
        when(verifier.verify(anyString(), anyString(), any(byte[].class))).thenReturn(true);

        SignatureVerificationFilter filter = new SignatureVerificationFilter(verifier);

        String body = "{\"type\":2,\"id\":\"123\"}";
        MockHttpServletRequest request = postInteractions(body);
        request.addHeader("X-Signature-Ed25519", "aabbcc");
        request.addHeader("X-Signature-Timestamp", "99999");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(verifier).verify("aabbcc", "99999", body.getBytes());
    }

    // ---- helper ----

    private static MockHttpServletRequest postInteractions(String body) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/discord/interactions");
        request.setContent(body.getBytes());
        request.setContentType("application/json");
        return request;
    }
}
