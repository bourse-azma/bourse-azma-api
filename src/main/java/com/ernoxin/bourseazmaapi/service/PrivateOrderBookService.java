package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.PrivateOrderBookLevelResponse;
import com.ernoxin.bourseazmaapi.dto.PrivateOrderBookResponse;
import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PrivateOrderBookService {

    private static final int DISPLAY_LEVELS = 5;
    private static final List<OrderStatus> VISIBLE_STATUSES =
            List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED);

    private final TradingOrderRepository tradingOrderRepository;
    private final MarketLiquidityService marketLiquidityService;

    /**
     * Builds a user's isolated simulated book by overlaying only that user's resting orders on
     * the public market snapshot. Orders owned by every other application user are deliberately
     * excluded, so an abusive demo account cannot change this view or its matching outcome.
     */
    @Transactional(readOnly = true)
    public PrivateOrderBookResponse getOrderBook(Long userId, String instrumentCode) {
        String normalizedCode = normalizeInstrumentCode(instrumentCode);

        Map<BigDecimal, MutableLevel> bids = new TreeMap<>(Comparator.reverseOrder());
        Map<BigDecimal, MutableLevel> asks = new TreeMap<>();

        for (MarketLiquidityLevel level : marketLiquidityService.getBidLevels(normalizedCode)) {
            mergeMarketLevel(bids, level);
        }
        for (MarketLiquidityLevel level : marketLiquidityService.getAskLevels(normalizedCode)) {
            mergeMarketLevel(asks, level);
        }

        List<TradingOrder> ownOrders = tradingOrderRepository.findActiveOrdersForPrivateBook(
                userId, normalizedCode, VISIBLE_STATUSES);
        for (TradingOrder order : ownOrders) {
            Map<BigDecimal, MutableLevel> side = order.getSide() == OrderSide.BUY ? bids : asks;
            MutableLevel level = side.computeIfAbsent(order.getOrderPrice(), ignored -> new MutableLevel());
            level.volume += order.getRemainingQuantity();
            level.orderCount++;
            level.ownVolume += order.getRemainingQuantity();
        }

        List<Map.Entry<BigDecimal, MutableLevel>> bidLevels = firstLevels(bids);
        List<Map.Entry<BigDecimal, MutableLevel>> askLevels = firstLevels(asks);
        int rowCount = Math.max(bidLevels.size(), askLevels.size());
        List<PrivateOrderBookLevelResponse> rows = new ArrayList<>(rowCount);
        for (int index = 0; index < rowCount; index++) {
            Map.Entry<BigDecimal, MutableLevel> bid = entryAt(bidLevels, index);
            Map.Entry<BigDecimal, MutableLevel> ask = entryAt(askLevels, index);
            rows.add(new PrivateOrderBookLevelResponse(
                    index + 1,
                    ask == null ? null : ask.getKey(),
                    ask == null ? 0 : ask.getValue().volume,
                    ask == null ? 0 : ask.getValue().orderCount,
                    ask == null ? 0 : ask.getValue().ownVolume,
                    bid == null ? null : bid.getKey(),
                    bid == null ? 0 : bid.getValue().volume,
                    bid == null ? 0 : bid.getValue().orderCount,
                    bid == null ? 0 : bid.getValue().ownVolume
            ));
        }

        return new PrivateOrderBookResponse(normalizedCode, List.copyOf(rows), Instant.now());
    }

    private void mergeMarketLevel(Map<BigDecimal, MutableLevel> levels, MarketLiquidityLevel source) {
        MutableLevel target = levels.computeIfAbsent(source.price(), ignored -> new MutableLevel());
        target.volume += source.volume();
        target.orderCount += source.orderCount();
    }

    private List<Map.Entry<BigDecimal, MutableLevel>> firstLevels(Map<BigDecimal, MutableLevel> levels) {
        return levels.entrySet().stream().limit(DISPLAY_LEVELS).toList();
    }

    private Map.Entry<BigDecimal, MutableLevel> entryAt(
            List<Map.Entry<BigDecimal, MutableLevel>> levels, int index) {
        return index < levels.size() ? levels.get(index) : null;
    }

    private String normalizeInstrumentCode(String instrumentCode) {
        String normalized = instrumentCode == null ? "" : instrumentCode.trim();
        if (normalized.isEmpty() || normalized.length() > 60) {
            throw new IllegalArgumentException("کد ابزار معتبر نیست.");
        }
        return normalized;
    }

    private static final class MutableLevel {
        private long volume;
        private long orderCount;
        private long ownVolume;
    }
}
