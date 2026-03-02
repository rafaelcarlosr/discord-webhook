package dev.discord.wsgw.redis;

import dev.discord.wsgw.config.BrokerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EventPublisherTest {

    private ReactiveStringRedisTemplate redisTemplate;
    @SuppressWarnings("rawtypes")
    private ReactiveStreamOperations streamOps;
    private EventPublisher publisher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        streamOps = mock(ReactiveStreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        publisher = new EventPublisher(redisTemplate, new BrokerProperties("discord"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishUsesPartitionedStreamKey() {
        RecordId recordId = RecordId.of("1700000000-0");
        when(streamOps.add(any(StringRecord.class))).thenReturn(Mono.just(recordId));

        StepVerifier.create(publisher.publish("MESSAGE_CREATE", "{\"content\":\"hello\"}"))
                .expectNext(recordId)
                .verifyComplete();

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        assertThat(captor.getValue().getStream()).isEqualTo("discord:MESSAGE_CREATE");
        assertThat(captor.getValue().getValue()).containsEntry("payload", "{\"content\":\"hello\"}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishRespectsConfiguredBrokerGroup() {
        when(streamOps.add(any(StringRecord.class))).thenReturn(Mono.just(RecordId.of("0-1")));
        var customPublisher = new EventPublisher(redisTemplate, new BrokerProperties("mybot"));

        StepVerifier.create(customPublisher.publish("GUILD_MEMBER_ADD", "{}"))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps).add(captor.capture());
        assertThat(captor.getValue().getStream()).isEqualTo("mybot:GUILD_MEMBER_ADD");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishEmbedsDifferentEventTypesInStreamKey() {
        when(streamOps.add(any(StringRecord.class))).thenReturn(Mono.just(RecordId.of("0-2")));

        publisher.publish("GUILD_MEMBER_ADD", "{}").block();
        publisher.publish("MESSAGE_DELETE", "{}").block();

        ArgumentCaptor<StringRecord> captor = ArgumentCaptor.forClass(StringRecord.class);
        verify(streamOps, times(2)).add(captor.capture());
        assertThat(captor.getAllValues().get(0).getStream()).isEqualTo("discord:GUILD_MEMBER_ADD");
        assertThat(captor.getAllValues().get(1).getStream()).isEqualTo("discord:MESSAGE_DELETE");
    }
}
