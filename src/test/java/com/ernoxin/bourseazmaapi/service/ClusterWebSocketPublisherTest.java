package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.WebSocketProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusterWebSocketPublisherTest {

    @Mock
    private SimpMessagingTemplate firstNodeBroker;

    @Mock
    private SimpMessagingTemplate secondNodeBroker;

    @Mock
    private StringRedisTemplate redisTemplate;

    private WebSocketProperties properties;
    private ObjectMapper objectMapper;
    private ClusterWebSocketPublisher firstNode;
    private ClusterWebSocketPublisher secondNode;

    @BeforeEach
    void setUp() {
        properties = new WebSocketProperties();
        properties.getRedisFanout().setEnabled(true);
        objectMapper = new ObjectMapper();
        firstNode = new ClusterWebSocketPublisher(firstNodeBroker, redisTemplate, objectMapper, properties);
        secondNode = new ClusterWebSocketPublisher(secondNodeBroker, redisTemplate, objectMapper, properties);
    }

    @Test
    void broadcastsLocallyAndAcrossAnotherReplicaWithoutEchoingOnTheOrigin() {
        firstNode.send("/topic/market/TEST", Map.of("price", 123));

        verify(firstNodeBroker).convertAndSend("/topic/market/TEST", Map.of("price", 123));
        String envelope = capturedEnvelope();

        secondNode.receive(envelope);
        ArgumentCaptor<Object> remotePayload = ArgumentCaptor.forClass(Object.class);
        verify(secondNodeBroker).convertAndSend(eq("/topic/market/TEST"), remotePayload.capture());
        assertThat((JsonNode) remotePayload.getValue()).isEqualTo(objectMapper.valueToTree(Map.of("price", 123)));

        firstNode.receive(envelope);
        verify(firstNodeBroker, times(1)).convertAndSend(
                eq("/topic/market/TEST"), any(Object.class));
    }

    @Test
    void routesPrivateMessagesOnlyThroughTheTargetUsersDestinationOnEveryReplica() {
        firstNode.sendToUser("alice", "/queue/orders", Map.of("orderId", 42));

        verify(firstNodeBroker).convertAndSendToUser("alice", "/queue/orders", Map.of("orderId", 42));
        secondNode.receive(capturedEnvelope());

        ArgumentCaptor<Object> remotePayload = ArgumentCaptor.forClass(Object.class);
        verify(secondNodeBroker).convertAndSendToUser(
                eq("alice"), eq("/queue/orders"), remotePayload.capture());
        assertThat((JsonNode) remotePayload.getValue()).isEqualTo(objectMapper.valueToTree(Map.of("orderId", 42)));
        verifyNoMoreInteractions(secondNodeBroker);
    }

    @Test
    void preservesLocalDeliveryWhenRedisIsTemporarilyUnavailable() {
        when(redisTemplate.convertAndSend(any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatCode(() -> firstNode.send("/topic/market/TEST", "update"))
                .doesNotThrowAnyException();

        verify(firstNodeBroker).convertAndSend("/topic/market/TEST", "update");
    }

    @Test
    void doesNotPublishToRedisWhenFanoutIsExplicitlyDisabled() {
        properties.getRedisFanout().setEnabled(false);

        firstNode.send("/topic/market/TEST", "update");

        verify(firstNodeBroker).convertAndSend("/topic/market/TEST", "update");
        verifyNoInteractions(redisTemplate);
    }

    private String capturedEnvelope() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(
                eq(properties.getRedisFanout().getChannel()), payload.capture());
        return payload.getValue();
    }
}
