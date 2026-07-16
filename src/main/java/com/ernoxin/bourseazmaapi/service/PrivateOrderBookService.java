package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.PrivateOrderBookLevelResponse;
import com.ernoxin.bourseazmaapi.dto.PrivateOrderBookResponse;
import com.ernoxin.bourseazmaapi.model.OrderSide;
import com.ernoxin.bourseazmaapi.model.OrderStatus;
import com.ernoxin.bourseazmaapi.model.TradingOrder;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.ResidualBookLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Builds a user's isolated simulated book from the same residual-market + own-resting state
 * used by the matching engine.
 */
@Service
@RequiredArgsConstructor
public class PrivateOrderBookService {

    private static final int DISPLAY_LEVELS = 5;
    private static final List<OrderStatus> VISIBLE_STATUSES =
            List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED);

    private final TradingOrderRepository tradingOrderRepository;
    private final PrivateBookStateService privateBookStateService;

    /**
     * Residual public depth (after this user's prior takes) overlaid with only this user's
     * resting orders. Other application users are excluded entirely.
     */
    @Transactional(readOnly = true)
    public PrivateOrderBookResponse getOrderBook(Long userId, String instrumentCode) {
        String normalizedCode = normalizeInstrumentCode(instrumentCode);

        NavigableMap<BigDecimal, MutableLevel> bids = new TreeMap<>(Comparator.reverseOrder());
        NavigableMap<BigDecimal, MutableLevel> asks = new TreeMap<>();

        for (ResidualBookLevel level : privateBookStateService.loadResidualBidLevels(userId, normalizedCode)) {
            mergeResidualLevel(bids, level);
        }
        for (ResidualBookLevel level : privateBookStateService.loadResidualAskLevels(userId, normalizedCode)) {
            mergeResidualLevel(asks, level);
        }

        List<TradingOrder> ownOrders = tradingOrderRepository.findActiveOrdersForPrivateBook(
                userId, normalizedCode, VISIBLE_STATUSES);
        for (TradingOrder order : ownOrders) {
            if (order == null || order.getSide() == null || order.getOrderPrice() == null
                    || order.getRemainingQuantity() == null || order.getRemainingQuantity() <= 0) {
                continue;
            }

            Map<BigDecimal, MutableLevel> side = order.getSide() == OrderSide.BUY ? bids : asks;
            BigDecimal price = order.getOrderPrice().setScale(2, java.math.RoundingMode.HALF_UP);
            MutableLevel level = side.computeIfAbsent(price, ignored -> new MutableLevel());
            level.volume += order.getRemainingQuantity();
            level.orderCount++;
            level.ownVolume += order.getRemainingQuantity();
            Instant orderTime = order.getOrderTime() != null ? order.getOrderTime() : Instant.EPOCH;
            if (level.latestOwnOrderTime == null || orderTime.isAfter(level.latestOwnOrderTime)) {
                level.latestOwnOrderTime = orderTime;
            }
        }

        // This is a personalized five-row view, not a raw top-five market snapshot. Every own
        // price level gets a display slot first; remaining slots are filled from the best public
        // levels. Prices are never rewritten or merged into a neighbouring level.
        List<Map.Entry<BigDecimal, MutableLevel>> bidLevels = displayLevels(bids);
        List<Map.Entry<BigDecimal, MutableLevel>> askLevels = displayLevels(asks);

        List<PrivateOrderBookLevelResponse> rows = new ArrayList<>(DISPLAY_LEVELS);
        for (int index = 0; index < DISPLAY_LEVELS; index++) {
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

    private void mergeResidualLevel(Map<BigDecimal, MutableLevel> levels, ResidualBookLevel source) {
        MutableLevel target = levels.computeIfAbsent(source.price(), ignored -> new MutableLevel());
        target.volume += source.residualVolume();
        target.orderCount += source.displayOrderCount();
    }

    private List<Map.Entry<BigDecimal, MutableLevel>> displayLevels(
            NavigableMap<BigDecimal, MutableLevel> levels) {
        List<Map.Entry<BigDecimal, MutableLevel>> selected = new ArrayList<>(DISPLAY_LEVELS);
        Set<BigDecimal> selectedPrices = new HashSet<>();

        // If a user has more than five distinct own prices, the most recently submitted
        // levels stay visible. The complete set of orders remains available in the orders tab.
        levels.entrySet().stream()
                .filter(entry -> entry.getValue().ownVolume > 0)
                .sorted(Comparator.comparing(
                        (Map.Entry<BigDecimal, MutableLevel> entry) -> entry.getValue().latestOwnOrderTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ).reversed())
                .limit(DISPLAY_LEVELS)
                .forEach(entry -> {
                    selected.add(entry);
                    selectedPrices.add(entry.getKey());
                });

        for (Map.Entry<BigDecimal, MutableLevel> entry : levels.entrySet()) {
            if (selected.size() >= DISPLAY_LEVELS) {
                break;
            }
            if (selectedPrices.add(entry.getKey())) {
                selected.add(entry);
            }
        }

        Comparator<? super BigDecimal> priceComparator = levels.comparator();
        selected.sort((left, right) -> priceComparator == null
                ? left.getKey().compareTo(right.getKey())
                : priceComparator.compare(left.getKey(), right.getKey()));
        return List.copyOf(selected);
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
        private Instant latestOwnOrderTime;
    }
}
