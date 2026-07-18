package com.ernoxin.bourseazmaapi.websocket;

import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.security.AppUserPrincipal;
import com.ernoxin.bourseazmaapi.security.JwtTokenService;
import com.ernoxin.bourseazmaapi.security.RevokedTokenService;
import com.ernoxin.bourseazmaapi.service.ClusterWebSocketPublisher;
import com.ernoxin.bourseazmaapi.service.marketsearch.MarketCsvLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:websocket-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "app.cors.allowed-origins=http://localhost",
                "app.cors.allowed-methods=GET,POST",
                "app.cors.allowed-headers=*",
                "app.cors.allow-credentials=true",
                "app.cors.max-age-seconds=60",
                "app.security.jwt.access-token-minutes=5",
                "app.order-matching.enabled=false",
                "app.market-stream.initial-delay-ms=3600000"
        }
)
class WebSocketJwtIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private ClusterWebSocketPublisher clusterWebSocketPublisher;

    @MockitoBean
    private RevokedTokenService revokedTokenService;

    @MockitoBean
    private MarketCsvLoader marketCsvLoader;

    private WebSocketStompClient stompClient;
    private String accessToken;
    private StompSession session;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("websocket-user");
        user.setFirstName("WebSocket");
        user.setLastName("Test");
        user.setEmail("websocket-test@example.com");
        user.setPhoneNumber("09120000001");
        user.setPassword("encoded-password");
        user.setRole(UserRole.USER);
        User existing = userRepository.findByUsername(user.getUsername()).orElse(null);
        user = existing != null ? existing : userRepository.save(user);
        accessToken = jwtTokenService.generateAccessToken(AppUserPrincipal.from(user));

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
    }

    @Test
    void authenticatedClientConnectsSubscribesAndReceivesMessage() throws Exception {
        session = connect(accessToken);
        CountDownLatch received = new CountDownLatch(1);
        String[] payload = new String[1];

        session.subscribe("/topic/market/TEST1", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object framePayload) {
                payload[0] = (String) framePayload;
                received.countDown();
            }
        });

        // Give the broker time to register SUBSCRIBE before publishing.
        Thread.sleep(100);
        clusterWebSocketPublisher.send("/topic/market/TEST1", "market-update");

        assertTrue(received.await(5, TimeUnit.SECONDS));
        assertEquals("market-update", payload[0]);
    }

    @Test
    void connectionWithoutJwtIsRejected() {
        assertThrows(ExecutionException.class, () -> connectFuture(null).get(5, TimeUnit.SECONDS));
    }

    @Test
    void connectionWithInvalidJwtIsRejected() {
        assertThrows(ExecutionException.class, () -> connectFuture("not-a-jwt").get(5, TimeUnit.SECONDS));
    }

    @Test
    void privateOrderUpdatesAreDeliveredOnlyToTheTargetUser() throws Exception {
        StompSession firstUserSession = connect(accessToken);
        User secondUser = new User();
        secondUser.setUsername("other-websocket-user");
        secondUser.setFirstName("Other");
        secondUser.setLastName("User");
        secondUser.setEmail("other-websocket-test@example.com");
        secondUser.setPhoneNumber("09120000002");
        secondUser.setPassword("encoded-password");
        secondUser.setRole(UserRole.USER);
        User existing = userRepository.findByUsername(secondUser.getUsername()).orElse(null);
        secondUser = existing != null ? existing : userRepository.save(secondUser);
        String secondToken = jwtTokenService.generateAccessToken(AppUserPrincipal.from(secondUser));
        StompSession secondUserSession = connect(secondToken);

        CountDownLatch firstUserReceived = new CountDownLatch(1);
        CountDownLatch secondUserReceived = new CountDownLatch(1);
        firstUserSession.subscribe("/user/queue/orders", stringFrame(firstUserReceived));
        secondUserSession.subscribe("/user/queue/orders", stringFrame(secondUserReceived));

        Thread.sleep(100);
        clusterWebSocketPublisher.sendToUser("websocket-user", "/queue/orders", "private-order-update");

        assertTrue(firstUserReceived.await(5, TimeUnit.SECONDS));
        assertFalse(secondUserReceived.await(300, TimeUnit.MILLISECONDS));
        firstUserSession.disconnect();
        secondUserSession.disconnect();
    }

    private StompFrameHandler stringFrame(CountDownLatch received) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.countDown();
            }
        };
    }

    private StompSession connect(String token) throws Exception {
        return connectFuture(token).get(5, TimeUnit.SECONDS);
    }

    private java.util.concurrent.CompletableFuture<StompSession> connectFuture(String token) {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        return stompClient.connectAsync(
                "ws://localhost:" + port + "/ws-api",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        );
    }
}
