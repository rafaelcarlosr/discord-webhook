package dev.discord.gateway.service;

import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EventForwardingService {

    private static final String STREAM_KEY = "discord:events";

    private final StringRedisTemplate redisTemplate;

    public EventForwardingService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Pushes the raw JSON payload onto the {@code discord:events} Redis Stream.
     *
     * @return the auto-generated {@link RecordId} assigned by Redis
     */
    public RecordId forward(String payload) {
        StringRecord record = StringRecord.of(Map.of("payload", payload))
                .withStreamKey(STREAM_KEY);
        return redisTemplate.opsForStream().add(record);
    }
}
