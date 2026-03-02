package dev.discord.gateway.service;

import dev.discord.gateway.config.BrokerProperties;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EventForwardingService {

    private static final String EVENT_TYPE = "INTERACTION_CREATE";

    private final StringRedisTemplate redisTemplate;
    private final BrokerProperties broker;

    public EventForwardingService(StringRedisTemplate redisTemplate, BrokerProperties broker) {
        this.redisTemplate = redisTemplate;
        this.broker = broker;
    }

    /**
     * Pushes the raw JSON payload onto the {@code {broker.group}:INTERACTION_CREATE} Redis Stream.
     * Stream key follows the spectacles broker convention, e.g. {@code discord:INTERACTION_CREATE}.
     *
     * @return the auto-generated {@link RecordId} assigned by Redis
     */
    public RecordId forward(String payload) {
        String streamKey = broker.group() + ":" + EVENT_TYPE;
        StringRecord record = StringRecord.of(Map.of("payload", payload))
                .withStreamKey(streamKey);
        return redisTemplate.opsForStream().add(record);
    }
}
