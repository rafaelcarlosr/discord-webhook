package dev.discord.gateway.config;

import dev.discord.gateway.crypto.Ed25519Verifier;
import dev.discord.gateway.filter.SignatureVerificationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public Ed25519Verifier ed25519Verifier(
            @Value("${discord.public-key}") String publicKey) {
        return new Ed25519Verifier(publicKey);
    }

    @Bean
    public FilterRegistrationBean<SignatureVerificationFilter> signatureVerificationFilter(
            Ed25519Verifier verifier) {
        var registration = new FilterRegistrationBean<>(new SignatureVerificationFilter(verifier));
        registration.addUrlPatterns("/api/discord/interactions");
        registration.setOrder(1);
        return registration;
    }
}
