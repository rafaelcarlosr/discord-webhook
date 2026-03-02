package dev.discord.wsgw.redis;

import dev.discord.wsgw.config.BrokerProperties;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Publishes Discord Gateway events to Redis Streams.
 *
 * <p>Stream key pattern: {@code {broker.group}:{EVENT_TYPE}}
 * e.g. {@code discord:MESSAGE_CREATE}, {@code discord:GUILD_MEMBER_ADD}.
 *
 * <p>This matches the spectacles broker convention, making it straightforward
 * to replace this gateway with a spectacles-compatible implementation.
 */
@Component
public class EventPublisher {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final BrokerProperties broker;

    public EventPublisher(ReactiveStringRedisTemplate redisTemplate, BrokerProperties broker) {
        this.redisTemplate = redisTemplate;
        this.broker = broker;
    }

    public Mono<RecordId> publish(String eventType, String payload) {
        String streamKey = broker.group() + ":" + eventType;
        StringRecord record = StringRecord.of(Map.of("payload", payload)).withStreamKey(streamKey);
        return redisTemplate.opsForStream().add(record);
    }
}
