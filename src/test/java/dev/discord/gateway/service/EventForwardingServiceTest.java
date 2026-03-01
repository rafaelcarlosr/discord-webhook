package dev.discord.gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventForwardingServiceTest {

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private StreamOperations streamOps;
    private EventForwardingService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        service = new EventForwardingService(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardPublishesToDiscordEventsStream() {
        RecordId expectedId = RecordId.of("1700000000-0");
        when(streamOps.add(any(StringRecord.class))).thenReturn(expectedId);

        String payload = "{\"type\":2,\"id\":\"abc\"}";
        RecordId result = service.forward(payload);

        assertThat(result).isEqualTo(expectedId);

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());

        StringRecord record = captor.getValue();
        assertThat(record.getStream()).isEqualTo("discord:events");
        assertThat(record.getValue()).containsEntry("payload", payload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardCallsOpsForStreamOnEveryInvocation() {
        when(streamOps.add(any(StringRecord.class))).thenReturn(RecordId.of("0-1"));

        service.forward("{\"type\":2}");
        service.forward("{\"type\":3}");

        verify(streamOps, times(2)).add(any(StringRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardReturnsRecordIdFromRedis() {
        RecordId id = RecordId.of("9999999999-1");
        when(streamOps.add(any(StringRecord.class))).thenReturn(id);

        assertThat(service.forward("{}")).isSameAs(id);
    }
}
