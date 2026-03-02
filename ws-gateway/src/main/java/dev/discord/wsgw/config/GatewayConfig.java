package dev.discord.wsgw.config;

import dev.discord.wsgw.gateway.GatewaySession;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.socket.client.ReactiveWebSocketClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

@Configuration
@EnableConfigurationProperties({DiscordProperties.class, BrokerProperties.class})
public class GatewayConfig {

    @Bean
    public ReactiveWebSocketClient webSocketClient() {
        return new ReactorNettyWebSocketClient();
    }

    @Bean
    public GatewaySession gatewaySession() {
        return new GatewaySession();
    }
}
