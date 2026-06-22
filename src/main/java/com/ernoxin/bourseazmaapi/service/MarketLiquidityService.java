package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.client.TsetmcMarketClient;
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

    private final TsetmcMarketClient tsetmcMarketClient;

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
        Optional<JsonNode> payload = tsetmcMarketClient.getBestLimits(instrumentCode);
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
}
