package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.client.TsetmcMarketClient;
import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarketLiquidityService {

    private static final long CACHE_TTL_MS = 2_000;
    private final TsetmcMarketClient tsetmcMarketClient;
    private final java.util.concurrent.ConcurrentHashMap<String, CachedPayload> bestLimitsCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private Optional<com.fasterxml.jackson.databind.JsonNode> getCachedBestLimits(String instrumentCode) {
        CachedPayload cached = bestLimitsCache.get(instrumentCode);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.timestamp()) < CACHE_TTL_MS) {
            return cached.payload();
        }
        Optional<com.fasterxml.jackson.databind.JsonNode> fresh = tsetmcMarketClient.getBestLimits(instrumentCode);
        bestLimitsCache.put(instrumentCode, new CachedPayload(fresh, now));
        if (bestLimitsCache.size() > 200) {
            bestLimitsCache.entrySet().removeIf(e -> (now - e.getValue().timestamp()) >= CACHE_TTL_MS);
        }
        return fresh;
    }

    public List<MarketLiquidityLevel> getAskLevels(String instrumentCode) {
        return parseLevels(instrumentCode, Side.ASK).stream()
                .sorted(Comparator.comparing(MarketLiquidityLevel::price))
                .toList();
    }

    public List<MarketLiquidityLevel> getBidLevels(String instrumentCode) {
        return parseLevels(instrumentCode, Side.BID).stream()
                .sorted(Comparator.comparing(MarketLiquidityLevel::price).reversed())
                .toList();
    }

    public Optional<OrderBookPriceRange> getBidPriceRange(String instrumentCode) {
        return toPriceRange(getBidLevels(instrumentCode));
    }

    public Optional<OrderBookPriceRange> getAskPriceRange(String instrumentCode) {
        return toPriceRange(getAskLevels(instrumentCode));
    }

    public boolean isOrderBookReady(String instrumentCode) {
        String normalizedCode = instrumentCode == null ? "" : instrumentCode.trim();
        if (normalizedCode.isEmpty()) {
            return false;
        }
        return getBidPriceRange(normalizedCode).isPresent() && getAskPriceRange(normalizedCode).isPresent();
    }

    public BigDecimal resolveMarketOrderPrice(String instrumentCode, OrderSide side) {
        String normalizedCode = instrumentCode == null ? "" : instrumentCode.trim();
        if (normalizedCode.isEmpty()) {
            throw new IllegalArgumentException("کد نماد معتبر نیست.");
        }
        if (side == OrderSide.BUY) {
            return getAskPriceRange(normalizedCode)
                    .map(OrderBookPriceRange::max)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "اطلاعات صف خرید و فروش در دسترس نیست؛ امکان ثبت سفارش وجود ندارد."));
        }
        return getBidPriceRange(normalizedCode)
                .map(OrderBookPriceRange::min)
                .orElseThrow(() -> new IllegalArgumentException(
                        "اطلاعات صف خرید و فروش در دسترس نیست؛ امکان ثبت سفارش وجود ندارد."));
    }

    public java.util.Optional<BigDecimal> getReferencePrice(String instrumentCode) {
        String normalizedCode = instrumentCode == null ? "" : instrumentCode.trim();
        if (normalizedCode.isEmpty()) {
            return java.util.Optional.empty();
        }

        List<MarketLiquidityLevel> bids = getBidLevels(normalizedCode);
        List<MarketLiquidityLevel> asks = getAskLevels(normalizedCode);
        if (bids.isEmpty() && asks.isEmpty()) {
            return java.util.Optional.empty();
        }
        if (!bids.isEmpty() && !asks.isEmpty()) {
            BigDecimal mid = bids.get(0).price().add(asks.get(0).price())
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            return java.util.Optional.of(mid);
        }
        if (!asks.isEmpty()) {
            return java.util.Optional.of(asks.get(0).price());
        }
        return java.util.Optional.of(bids.get(0).price());
    }

    private Optional<OrderBookPriceRange> toPriceRange(List<MarketLiquidityLevel> levels) {
        if (levels.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal min = levels.stream()
                .map(MarketLiquidityLevel::price)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        BigDecimal max = levels.stream()
                .map(MarketLiquidityLevel::price)
                .max(BigDecimal::compareTo)
                .orElseThrow();
        return Optional.of(new OrderBookPriceRange(min, max));
    }

    private List<MarketLiquidityLevel> parseLevels(String instrumentCode, Side side) {
        Optional<JsonNode> payload = getCachedBestLimits(instrumentCode);
        if (payload.isEmpty()) {
            return List.of();
        }

        JsonNode levels = payload.get().get("orderBookLevels");
        if (levels == null || !levels.isArray()) {
            return List.of();
        }

        List<MarketLiquidityLevel> parsed = new ArrayList<>();
        for (JsonNode level : levels) {
            BigDecimal price = toPrice(side == Side.ASK ? level.get("askPrice") : level.get("bidPrice"));
            long volume = toVolume(side == Side.ASK ? level.get("askVolume") : level.get("bidVolume"));
            int levelNumber = level.hasNonNull("levelNumber") ? level.get("levelNumber").asInt(0) : 0;
            if (price == null || volume <= 0) {
                continue;
            }
            parsed.add(new MarketLiquidityLevel(levelNumber, price, volume));
        }
        return parsed;
    }

    private BigDecimal toPrice(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isNumber()) {
            return null;
        }
        double value = node.asDouble();
        if (!Double.isFinite(value) || value <= 0) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private long toVolume(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return 0L;
        }
        double value = node.asDouble();
        if (!Double.isFinite(value) || value <= 0) {
            return 0L;
        }
        return (long) Math.floor(value);
    }

    private enum Side {
        ASK,
        BID
    }

    private record CachedPayload(Optional<com.fasterxml.jackson.databind.JsonNode> payload, long timestamp) {
    }
}
