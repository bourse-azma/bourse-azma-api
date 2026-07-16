package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.client.TsetmcMarketClient;
import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketLiquidityServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesFiltersAndSortsBothSidesWithoutTrustingMalformedLevels() throws Exception {
        TsetmcMarketClient client = mock(TsetmcMarketClient.class);
        when(client.getBestLimits("IRO1TEST0001")).thenReturn(Optional.of(payload("""
                [
                  {"levelNumber":2,"askPrice":110,"askVolume":20,"askOrderCount":2,
                   "bidPrice":90,"bidVolume":30,"bidOrderCount":3},
                  {"levelNumber":1,"askPrice":100,"askVolume":10,"askOrderCount":1,
                   "bidPrice":95,"bidVolume":15,"bidOrderCount":1},
                  {"levelNumber":3,"askPrice":0,"askVolume":99,"bidPrice":"bad","bidVolume":99},
                  {"levelNumber":4,"askPrice":120,"askVolume":-1,"bidPrice":80,"bidVolume":0}
                ]
                """)));
        MarketLiquidityService service = new MarketLiquidityService(client);

        List<MarketLiquidityLevel> asks = service.getAskLevels("IRO1TEST0001");
        List<MarketLiquidityLevel> bids = service.getBidLevels("IRO1TEST0001");

        assertThat(asks).extracting(MarketLiquidityLevel::price)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("110.00"));
        assertThat(bids).extracting(MarketLiquidityLevel::price)
                .containsExactly(new BigDecimal("95.00"), new BigDecimal("90.00"));
        verify(client, times(1)).getBestLimits("IRO1TEST0001");
    }

    @Test
    void marketPriceReservationUsesWorstVisiblePriceAndReferenceUsesBestMidpoint() throws Exception {
        TsetmcMarketClient client = mock(TsetmcMarketClient.class);
        when(client.getBestLimits("CODE")).thenReturn(Optional.of(payload("""
                [
                  {"askPrice":101,"askVolume":10,"bidPrice":99,"bidVolume":10},
                  {"askPrice":105,"askVolume":10,"bidPrice":95,"bidVolume":10}
                ]
                """)));
        MarketLiquidityService service = new MarketLiquidityService(client);

        assertThat(service.resolveMarketOrderPrice(" CODE ", OrderSide.BUY)).isEqualByComparingTo("105.00");
        assertThat(service.resolveMarketOrderPrice("CODE", OrderSide.SELL)).isEqualByComparingTo("95.00");
        assertThat(service.getReferencePrice("CODE")).contains(new BigDecimal("100.00"));
    }

    @Test
    void nullBlankAndMalformedPayloadsAreSafeEmptyResults() throws Exception {
        TsetmcMarketClient client = mock(TsetmcMarketClient.class);
        when(client.getBestLimits("MALFORMED")).thenReturn(Optional.of(objectMapper.readTree("{\"other\":[]}")));
        MarketLiquidityService service = new MarketLiquidityService(client);

        assertThat(service.getAskLevels(null)).isEmpty();
        assertThat(service.getBidLevels("   ")).isEmpty();
        assertThat(service.getAskLevels("MALFORMED")).isEmpty();
        verify(client, never()).getBestLimits(null);
        verify(client, never()).getBestLimits("   ");
    }

    @Test
    void simultaneousCacheMissesForOneInstrumentAreCoalescedIntoOneUpstreamCall() throws Exception {
        TsetmcMarketClient client = mock(TsetmcMarketClient.class);
        AtomicInteger upstreamCalls = new AtomicInteger();
        JsonNode payload = payload("[{\"askPrice\":100,\"askVolume\":10}]");
        when(client.getBestLimits("CODE")).thenAnswer(invocation -> {
            upstreamCalls.incrementAndGet();
            Thread.sleep(25);
            return Optional.of(payload);
        });
        MarketLiquidityService service = new MarketLiquidityService(client);
        int taskCount = 12;
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[taskCount];
            for (int index = 0; index < taskCount; index++) {
                futures[index] = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    assertThat(service.getAskLevels("CODE")).hasSize(1);
                    return null;
                });
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(upstreamCalls).hasValue(1);
    }

    private JsonNode payload(String levelsJson) throws Exception {
        return objectMapper.readTree("{\"orderBookLevels\":" + levelsJson + "}");
    }
}
