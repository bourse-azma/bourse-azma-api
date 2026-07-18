package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.client.TsetmcMarketClient;
import com.ernoxin.bourseazmaapi.dto.MarketDataUpdate;
import com.ernoxin.bourseazmaapi.dto.MarketOverviewUpdate;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Component
@Slf4j
public class MarketDataPushScheduler {

    private final TsetmcMarketClient marketClient;
    private final MarketSubscriptionRegistry subscriptionRegistry;
    private final ClusterWebSocketPublisher clusterWebSocketPublisher;
    private final Executor marketDataFetchExecutor;
    private final Map<String, MarketSnapshot> lastSnapshots = new ConcurrentHashMap<>();
    private final Map<Integer, JsonNode> lastOverviewSnapshots = new ConcurrentHashMap<>();

    public MarketDataPushScheduler(
            TsetmcMarketClient marketClient,
            MarketSubscriptionRegistry subscriptionRegistry,
            ClusterWebSocketPublisher clusterWebSocketPublisher,
            @Qualifier("marketDataFetchExecutor") Executor marketDataFetchExecutor) {
        this.marketClient = marketClient;
        this.subscriptionRegistry = subscriptionRegistry;
        this.clusterWebSocketPublisher = clusterWebSocketPublisher;
        this.marketDataFetchExecutor = marketDataFetchExecutor;
    }

    @Scheduled(
            fixedDelayString = "${app.market-stream.poll-interval-ms:4000}",
            initialDelayString = "${app.market-stream.initial-delay-ms:2000}"
    )
    public void pushChangedMarketData() {
        Set<String> activeCodes = subscriptionRegistry.activeInstrumentCodes();
        Set<Integer> activeMarketIds = subscriptionRegistry.activeMarketIds();
        lastSnapshots.keySet().retainAll(activeCodes);
        lastOverviewSnapshots.keySet().retainAll(activeMarketIds);
        if (activeCodes.isEmpty() && activeMarketIds.isEmpty()) {
            return;
        }

        var tasks = new ArrayList<CompletableFuture<Void>>(activeCodes.size() + activeMarketIds.size());
        for (String instrumentCode : activeCodes) {
            tasks.add(CompletableFuture.runAsync(
                    () -> fetchAndPublish(instrumentCode), marketDataFetchExecutor));
        }
        for (int marketId : activeMarketIds) {
            tasks.add(CompletableFuture.runAsync(
                    () -> fetchAndPublishOverview(marketId), marketDataFetchExecutor));
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
    }

    private void fetchAndPublish(String instrumentCode) {
        try {
            MarketSnapshot previous = lastSnapshots.get(instrumentCode);
            Optional<JsonNode> closingPrice = marketClient.getClosingPrice(instrumentCode);
            Optional<JsonNode> bestLimits = marketClient.getBestLimits(instrumentCode)
                    .map(result -> result.get("orderBookLevels"))
                    .filter(JsonNode::isArray);
            Optional<JsonNode> clientType = marketClient.getClientType(instrumentCode);

            if (closingPrice.isEmpty() && bestLimits.isEmpty() && clientType.isEmpty()) {
                return;
            }
            MarketSnapshot next = new MarketSnapshot(
                    closingPrice.orElse(previous == null ? null : previous.closingPrice()),
                    bestLimits.orElse(previous == null ? null : previous.bestLimits()),
                    clientType.orElse(previous == null ? null : previous.clientType())
            );
            if (next.isEmpty() || Objects.equals(previous, next)) {
                return;
            }

            lastSnapshots.put(instrumentCode, next);
            clusterWebSocketPublisher.send(
                    "/topic/market/" + instrumentCode,
                    new MarketDataUpdate(
                            instrumentCode,
                            next.closingPrice(),
                            next.bestLimits(),
                            next.clientType(),
                            Instant.now()
                    )
            );
        } catch (RuntimeException ex) {
            log.warn("Market WebSocket update failed for {}: {}", instrumentCode, ex.getMessage());
        }
    }

    private void fetchAndPublishOverview(int marketId) {
        try {
            marketClient.getMarketOverview(marketId).ifPresent(next -> {
                JsonNode previous = lastOverviewSnapshots.put(marketId, next);
                if (Objects.equals(previous, next)) {
                    return;
                }
                clusterWebSocketPublisher.send(
                        "/topic/market-overview/" + marketId,
                        new MarketOverviewUpdate(marketId, next.path("marketOverview"), Instant.now())
                );
            });
        } catch (RuntimeException ex) {
            log.warn("Market overview WebSocket update failed for {}: {}", marketId, ex.getMessage());
        }
    }

    private record MarketSnapshot(JsonNode closingPrice, JsonNode bestLimits, JsonNode clientType) {
        private boolean isEmpty() {
            return closingPrice == null && bestLimits == null && clientType == null;
        }
    }
}
