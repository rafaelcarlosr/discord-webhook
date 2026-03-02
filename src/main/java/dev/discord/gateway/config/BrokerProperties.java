package dev.discord.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configures the Redis Stream key prefix used for event routing.
 * Stream keys follow the pattern {@code {group}:{EVENT_TYPE}},
 * e.g. {@code discord:INTERACTION_CREATE} — compatible with the spectacles broker convention.
 */
@ConfigurationProperties(prefix = "discord.broker")
public record BrokerProperties(String group) {}
