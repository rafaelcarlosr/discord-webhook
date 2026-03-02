package dev.discord.wsgw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "discord")
public record DiscordProperties(
        String botToken,
        int intents,
        Shard shard) {

    public record Shard(int id, int total) {}
}
