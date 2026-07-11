package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.BookSide;
import com.ernoxin.bourseazmaapi.model.UserLiquidityConsumption;
import com.ernoxin.bourseazmaapi.repository.UserLiquidityConsumptionRepository;
import com.ernoxin.bourseazmaapi.service.ordermatching.ResidualBookLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds residual public depth for a single user's private simulation and records taker
 * consumption so the same static market snapshot cannot be filled infinitely.
 */
@Service
@RequiredArgsConstructor
public class PrivateBookStateService {

    private final MarketLiquidityService marketLiquidityService;
    private final UserLiquidityConsumptionRepository consumptionRepository;

    @Transactional(readOnly = true)
    public List<ResidualBookLevel> loadResidualAskLevels(Long userId, String instrumentCode) {
        return loadResidualLevels(userId, instrumentCode, BookSide.ASK,
                marketLiquidityService.getAskLevels(instrumentCode));
    }

    @Transactional(readOnly = true)
    public List<ResidualBookLevel> loadResidualBidLevels(Long userId, String instrumentCode) {
        return loadResidualLevels(userId, instrumentCode, BookSide.BID,
                marketLiquidityService.getBidLevels(instrumentCode));
    }

    @Transactional
    public void consume(Long userId, String instrumentCode, BookSide bookSide,
                        BigDecimal price, long quantity) {
        if (userId == null || instrumentCode == null || bookSide == null || price == null || quantity <= 0) {
            return;
        }
        BigDecimal normalizedPrice = price.setScale(2, RoundingMode.HALF_UP);
        UserLiquidityConsumption row = consumptionRepository
                .findByUserIdAndInstrumentCodeAndBookSideAndPrice(
                        userId, instrumentCode, bookSide, normalizedPrice)
                .orElseGet(() -> {
                    UserLiquidityConsumption created = new UserLiquidityConsumption();
                    created.setUserId(userId);
                    created.setInstrumentCode(instrumentCode);
                    created.setBookSide(bookSide);
                    created.setPrice(normalizedPrice);
                    created.setConsumedQuantity(0L);
                    return created;
                });
        long previous = row.getConsumedQuantity() == null ? 0L : row.getConsumedQuantity();
        row.setConsumedQuantity(previous + quantity);
        row.setUpdatedAt(Instant.now());
        consumptionRepository.save(row);
    }

    private List<ResidualBookLevel> loadResidualLevels(
            Long userId,
            String instrumentCode,
            BookSide bookSide,
            List<MarketLiquidityLevel> publicLevels) {

        Map<BigDecimal, Long> consumedByPrice = consumptionRepository
                .findAllByUserIdAndInstrumentCode(userId, instrumentCode)
                .stream()
                .filter(row -> row.getBookSide() == bookSide)
                .collect(Collectors.toMap(
                        row -> row.getPrice().setScale(2, RoundingMode.HALF_UP),
                        row -> row.getConsumedQuantity() == null ? 0L : row.getConsumedQuantity(),
                        Long::sum,
                        HashMap::new
                ));

        List<ResidualBookLevel> residual = new ArrayList<>(publicLevels.size());
        for (MarketLiquidityLevel level : publicLevels) {
            BigDecimal price = level.price().setScale(2, RoundingMode.HALF_UP);
            long consumed = consumedByPrice.getOrDefault(price, 0L);
            long remaining = Math.max(0L, level.volume() - consumed);
            if (remaining <= 0) {
                continue;
            }
            residual.add(new ResidualBookLevel(price, level.volume(), level.orderCount(), remaining));
        }
        return residual;
    }
}
