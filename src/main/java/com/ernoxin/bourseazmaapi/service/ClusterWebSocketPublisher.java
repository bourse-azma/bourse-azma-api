package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.WebSocketProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Delivers a STOMP message to sessions on this replica and fans the same message out through
 * Redis so every other API replica can deliver it to its own locally connected sessions.
 */
@Component
@Slf4j
public class ClusterWebSocketPublisher {

    private static final int MAX_ENVELOPE_LENGTH = 1_048_576;

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebSocketProperties webSocketProperties;
    private final String instanceId = UUID.randomUUID().toString();

    public ClusterWebSocketPublisher(
            SimpMessagingTemplate messagingTemplate,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WebSocketProperties webSocketProperties) {
        this.messagingTemplate = messagingTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.webSocketProperties = webSocketProperties;
    }

    public void send(String destination, Object payload) {
        requirePublicDestination(destination);
        publish(new ClusterMessage(instanceId, MessageType.BROADCAST, destination, null,
                objectMapper.valueToTree(payload)), payload);
    }

    public void sendToUser(String username, String destination, Object payload) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("A WebSocket username is required.");
        }
        requireUserDestination(destination);
        publish(new ClusterMessage(instanceId, MessageType.USER, destination, username,
                objectMapper.valueToTree(payload)), payload);
    }

    /**
     * Invoked by the Redis listener container for messages published by any API replica.
     */
    public void receive(String serializedMessage) {
        if (serializedMessage == null || serializedMessage.isBlank()
                || serializedMessage.length() > MAX_ENVELOPE_LENGTH) {
            log.warn("Ignoring an empty or oversized WebSocket fan-out message.");
            return;
        }

        try {
            ClusterMessage message = objectMapper.readValue(serializedMessage, ClusterMessage.class);
            if (instanceId.equals(message.originInstanceId())) {
                return;
            }
            deliverRemote(message);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            log.warn("Ignoring an invalid WebSocket fan-out message: {}", ex.getMessage());
        }
    }

    private void publish(ClusterMessage message, Object localPayload) {
        deliverLocal(message, localPayload);
        if (!webSocketProperties.getRedisFanout().isEnabled()) {
            return;
        }

        try {
            redisTemplate.convertAndSend(
                    webSocketProperties.getRedisFanout().getChannel(),
                    objectMapper.writeValueAsString(message)
            );
        } catch (RuntimeException | JsonProcessingException ex) {
            // Local delivery must remain available during a transient Redis outage. Reconnecting
            // clients perform an authoritative HTTP reconciliation for events missed remotely.
            log.error("WebSocket Redis fan-out publish failed: {}", ex.getMessage());
        }
    }

    private void deliverLocal(ClusterMessage message, Object payload) {
        if (message.type() == MessageType.USER) {
            messagingTemplate.convertAndSendToUser(message.username(), message.destination(), payload);
        } else {
            messagingTemplate.convertAndSend(message.destination(), payload);
        }
    }

    private void deliverRemote(ClusterMessage message) {
        if (message == null || message.payload() == null || message.type() == null) {
            throw new IllegalArgumentException("The WebSocket fan-out envelope is incomplete.");
        }
        if (message.type() == MessageType.USER) {
            if (message.username() == null || message.username().isBlank()) {
                throw new IllegalArgumentException("The WebSocket fan-out username is missing.");
            }
            requireUserDestination(message.destination());
            messagingTemplate.convertAndSendToUser(
                    message.username(), message.destination(), message.payload());
            return;
        }
        requirePublicDestination(message.destination());
        messagingTemplate.convertAndSend(message.destination(), message.payload());
    }

    private void requirePublicDestination(String destination) {
        if (destination == null || !destination.startsWith("/topic/")) {
            throw new IllegalArgumentException("A public WebSocket destination must start with /topic/.");
        }
    }

    private void requireUserDestination(String destination) {
        if (destination == null || !destination.startsWith("/queue/")) {
            throw new IllegalArgumentException("A user WebSocket destination must start with /queue/.");
        }
    }

    private enum MessageType {
        BROADCAST,
        USER
    }

    private record ClusterMessage(
            String originInstanceId,
            MessageType type,
            String destination,
            String username,
            JsonNode payload) {
    }
}
