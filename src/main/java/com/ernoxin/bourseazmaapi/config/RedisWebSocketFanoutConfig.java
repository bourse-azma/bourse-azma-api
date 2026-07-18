package com.ernoxin.bourseazmaapi.config;

import com.ernoxin.bourseazmaapi.service.ClusterWebSocketPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Configuration
@ConditionalOnProperty(
        prefix = "app.websocket.redis-fanout",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Slf4j
public class RedisWebSocketFanoutConfig {

    @Bean
    public RedisMessageListenerContainer webSocketRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ClusterWebSocketPublisher clusterWebSocketPublisher,
            WebSocketProperties webSocketProperties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setErrorHandler(error ->
                log.error("WebSocket Redis fan-out listener failed: {}", error.getMessage()));
        container.addMessageListener(
                (message, pattern) -> clusterWebSocketPublisher.receive(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(webSocketProperties.getRedisFanout().getChannel())
        );
        return container;
    }
}
