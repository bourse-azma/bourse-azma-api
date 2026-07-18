package com.ernoxin.bourseazmaapi.service;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MarketSubscriptionRegistry {

    private static final String MARKET_TOPIC_PREFIX = "/topic/market/";
    private static final String MARKET_OVERVIEW_TOPIC_PREFIX = "/topic/market-overview/";
    private static final String OVERVIEW_TARGET_PREFIX = "overview:";
    private static final int MAX_MARKET_SUBSCRIPTIONS_PER_SESSION = 100;
    private static final int MAX_ACTIVE_INSTRUMENTS = 500;
    private static final String INSTRUMENT_CODE_PATTERN = "[A-Za-z0-9_-]{1,80}";

    private final Map<String, Map<String, String>> subscriptionsBySession = new ConcurrentHashMap<>();

    public synchronized void register(String sessionId, String subscriptionId, String destination) {
        String target = extractTarget(destination);
        if (target == null) {
            return;
        }
        if (sessionId == null || subscriptionId == null) {
            throw new BadCredentialsException("A valid STOMP session and subscription id are required.");
        }
        boolean instrumentTarget = !target.startsWith(OVERVIEW_TARGET_PREFIX);
        boolean alreadyActive = subscriptionsBySession.values().stream()
                .anyMatch(subscriptions -> subscriptions.containsValue(target));
        if (instrumentTarget && !alreadyActive && activeInstrumentCodes().size() >= MAX_ACTIVE_INSTRUMENTS) {
            throw new BadCredentialsException("The global market subscription limit has been reached.");
        }

        Map<String, String> sessionSubscriptions = subscriptionsBySession.computeIfAbsent(
                sessionId, ignored -> new ConcurrentHashMap<>());
        if (!sessionSubscriptions.containsKey(subscriptionId)
                && sessionSubscriptions.size() >= MAX_MARKET_SUBSCRIPTIONS_PER_SESSION) {
            throw new BadCredentialsException("Too many market subscriptions for this WebSocket session.");
        }
        sessionSubscriptions.put(subscriptionId, target);
    }

    public void unregister(String sessionId, String subscriptionId) {
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        Map<String, String> sessionSubscriptions = subscriptionsBySession.get(sessionId);
        if (sessionSubscriptions == null) {
            return;
        }
        sessionSubscriptions.remove(subscriptionId);
        if (sessionSubscriptions.isEmpty()) {
            subscriptionsBySession.remove(sessionId, sessionSubscriptions);
        }
    }

    public void removeSession(String sessionId) {
        if (sessionId != null) {
            subscriptionsBySession.remove(sessionId);
        }
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        removeSession(event.getSessionId());
    }

    public Set<String> activeInstrumentCodes() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Map<String, String> subscriptions : subscriptionsBySession.values()) {
            for (String instrumentCode : subscriptions.values()) {
                if (instrumentCode.startsWith(OVERVIEW_TARGET_PREFIX)) {
                    continue;
                }
                result.add(instrumentCode);
                if (result.size() >= MAX_ACTIVE_INSTRUMENTS) {
                    return Set.copyOf(result);
                }
            }
        }
        return Set.copyOf(result);
    }

    public Set<Integer> activeMarketIds() {
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (Map<String, String> subscriptions : subscriptionsBySession.values()) {
            for (String target : subscriptions.values()) {
                if (target.startsWith(OVERVIEW_TARGET_PREFIX)) {
                    result.add(Integer.parseInt(target.substring(OVERVIEW_TARGET_PREFIX.length())));
                }
            }
        }
        return Set.copyOf(result);
    }

    private String extractTarget(String destination) {
        if (destination == null) {
            return null;
        }
        if (destination.startsWith(MARKET_OVERVIEW_TOPIC_PREFIX)) {
            String marketId = destination.substring(MARKET_OVERVIEW_TOPIC_PREFIX.length()).trim();
            if (!marketId.equals("1") && !marketId.equals("2")) {
                throw new BadCredentialsException("Invalid market overview subscription.");
            }
            return OVERVIEW_TARGET_PREFIX + marketId;
        }
        if (destination.startsWith(MARKET_TOPIC_PREFIX)) {
            String instrumentCode = destination.substring(MARKET_TOPIC_PREFIX.length()).trim();
            if (!instrumentCode.matches(INSTRUMENT_CODE_PATTERN)) {
                throw new BadCredentialsException("Invalid market instrument subscription.");
            }
            return instrumentCode;
        }
        return null;
    }
}
