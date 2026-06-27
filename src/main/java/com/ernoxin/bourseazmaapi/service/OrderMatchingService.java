package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradeRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderMatchingService {

    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(OrderStatus.REQUESTED, OrderStatus.PARTIALLY_FILLED);

    private final TradingOrderRepository tradingOrderRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final UserRepository userRepository;
    private final MarketLiquidityService marketLiquidityService;
    private final MarketMakerService marketMakerService;
    private final WalletLedgerService walletLedgerService;

    /**
     * Match the given incoming order against peer orders and TSETMC order-book liquidity.
     * The caller must have already saved the order with REQUESTED status.
     */
    @Transactional
    public List<Trade> matchOrder(TradingOrder incomingOrder) {
        if (isExpired(incomingOrder)) {
            return List.of();
        }
        List<Trade> trades = new ArrayList<>();
        if (incomingOrder.getSide() == OrderSide.BUY) {
            trades.addAll(matchBuyOrder(incomingOrder));
            if (incomingOrder.getRemainingQuantity() > 0) {
                trades.addAll(matchBuyAgainstMarket(incomingOrder));
            }
        } else {
            trades.addAll(matchSellOrder(incomingOrder));
            if (incomingOrder.getRemainingQuantity() > 0) {
                trades.addAll(matchSellAgainstMarket(incomingOrder));
            }
        }
        return trades;
    }

    /**
     * Re-run matching for all active orders on a given instrument.
     * Useful after cancellation frees liquidity or after TSETMC order-book updates.
     */
    @Transactional
    public List<Trade> runMatchingForInstrument(String instrumentCode) {
        List<Trade> allTrades = new ArrayList<>();

        List<TradingOrder> activeBuys = tradingOrderRepository.findActiveBuyOrders(
                instrumentCode, OrderSide.BUY, ACTIVE_STATUSES);
        for (TradingOrder buyOrder : activeBuys) {
            if (buyOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            allTrades.addAll(matchBuyOrder(buyOrder));
            if (buyOrder.getRemainingQuantity() > 0) {
                allTrades.addAll(matchBuyAgainstMarket(buyOrder));
            }
        }

        List<TradingOrder> activeSells = tradingOrderRepository.findActiveSellOrders(
                instrumentCode, OrderSide.SELL, ACTIVE_STATUSES);
        for (TradingOrder sellOrder : activeSells) {
            if (sellOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            allTrades.addAll(matchSellOrder(sellOrder));
            if (sellOrder.getRemainingQuantity() > 0) {
                allTrades.addAll(matchSellAgainstMarket(sellOrder));
            }
        }

        return allTrades;
    }

    @Transactional
    public List<Trade> runMatchingForAllActiveInstruments() {
        List<Trade> allTrades = new ArrayList<>();
        for (String instrumentCode : tradingOrderRepository.findDistinctInstrumentCodesWithActiveOrders()) {
            allTrades.addAll(runMatchingForInstrument(instrumentCode));
        }
        return allTrades;
    }

    private List<Trade> matchBuyOrder(TradingOrder buyOrder) {
        List<Trade> trades = new ArrayList<>();

        List<TradingOrder> sellOrders = tradingOrderRepository.findActiveSellOrders(
                buyOrder.getInstrumentCode(), OrderSide.SELL, ACTIVE_STATUSES);

        for (TradingOrder sellOrder : sellOrders) {
            if (buyOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (sellOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            if (marketMakerService.isMarketMaker(sellOrder.getUser())) {
                continue;
            }

            if (buyOrder.getOrderPrice().compareTo(sellOrder.getOrderPrice()) < 0) {
                break;
            }

            BigDecimal executionPrice = sellOrder.getOrderPrice();
            long matchQuantity = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());

            Trade trade = executeTrade(buyOrder, sellOrder, matchQuantity, executionPrice);
            if (trade != null) {
                trades.add(trade);
            }
        }

        return trades;
    }

    private List<Trade> matchSellOrder(TradingOrder sellOrder) {
        List<Trade> trades = new ArrayList<>();

        List<TradingOrder> buyOrders = tradingOrderRepository.findActiveBuyOrders(
                sellOrder.getInstrumentCode(), OrderSide.BUY, ACTIVE_STATUSES);

        for (TradingOrder buyOrder : buyOrders) {
            if (sellOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (buyOrder.getRemainingQuantity() <= 0) {
                continue;
            }
            if (marketMakerService.isMarketMaker(buyOrder.getUser())) {
                continue;
            }

            if (sellOrder.getOrderPrice().compareTo(buyOrder.getOrderPrice()) > 0) {
                break;
            }

            BigDecimal executionPrice = buyOrder.getOrderPrice();
            long matchQuantity = Math.min(sellOrder.getRemainingQuantity(), buyOrder.getRemainingQuantity());

            Trade trade = executeTrade(buyOrder, sellOrder, matchQuantity, executionPrice);
            if (trade != null) {
                trades.add(trade);
            }
        }

        return trades;
    }

    private List<Trade> matchBuyAgainstMarket(TradingOrder buyOrder) {
        if (!marketLiquidityService.isOrderBookReady(buyOrder.getInstrumentCode())) {
            return List.of();
        }

        List<Trade> trades = new ArrayList<>();
        List<MarketLiquidityLevel> askLevels = marketLiquidityService.getAskLevels(buyOrder.getInstrumentCode());

        for (MarketLiquidityLevel level : askLevels) {
            if (buyOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (buyOrder.getOrderPrice().compareTo(level.price()) < 0) {
                break;
            }

            long matchQuantity = Math.min(buyOrder.getRemainingQuantity(), level.volume());
            if (matchQuantity <= 0) {
                continue;
            }

            TradingOrder marketSellOrder = marketMakerService.createCompletedCounterOrder(
                    buyOrder, OrderSide.SELL, matchQuantity, level.price());
            Trade trade = executeTrade(buyOrder, marketSellOrder, matchQuantity, level.price());
            if (trade != null) {
                trades.add(trade);
            }
        }

        return trades;
    }

    private List<Trade> matchSellAgainstMarket(TradingOrder sellOrder) {
        if (!marketLiquidityService.isOrderBookReady(sellOrder.getInstrumentCode())) {
            return List.of();
        }

        List<Trade> trades = new ArrayList<>();
        List<MarketLiquidityLevel> bidLevels = marketLiquidityService.getBidLevels(sellOrder.getInstrumentCode());

        for (MarketLiquidityLevel level : bidLevels) {
            if (sellOrder.getRemainingQuantity() <= 0) {
                break;
            }
            if (sellOrder.getOrderPrice().compareTo(level.price()) > 0) {
                break;
            }

            long matchQuantity = Math.min(sellOrder.getRemainingQuantity(), level.volume());
            if (matchQuantity <= 0) {
                continue;
            }

            TradingOrder marketBuyOrder = marketMakerService.createCompletedCounterOrder(
                    sellOrder, OrderSide.BUY, matchQuantity, level.price());
            Trade trade = executeTrade(marketBuyOrder, sellOrder, matchQuantity, level.price());
            if (trade != null) {
                trades.add(trade);
            }
        }

        return trades;
    }

    private Trade executeTrade(TradingOrder buyOrder, TradingOrder sellOrder,
                               long quantity, BigDecimal price) {
        BigDecimal tradeValue = price.multiply(BigDecimal.valueOf(quantity));

        boolean buyerIsMarketMaker = marketMakerService.isMarketMaker(buyOrder.getUser());
        boolean sellerIsMarketMaker = marketMakerService.isMarketMaker(sellOrder.getUser());

        if (!buyerIsMarketMaker) {
            User buyer = userRepository.findByIdForUpdate(buyOrder.getUser().getId())
                    .orElse(null);
            if (buyer == null) {
                failBuyOrder(buyOrder, "حساب خریدار یافت نشد.");
                return null;
            }
            BigDecimal buyerBalance = buyer.getBalance() != null ? buyer.getBalance() : BigDecimal.ZERO;
            if (buyerBalance.compareTo(tradeValue) < 0) {
                failBuyOrder(buyOrder, "موجودی کافی برای اجرای سفارش خرید وجود ندارد.");
                return null;
            }
            buyOrder.setUser(buyer);
        }

        if (!sellerIsMarketMaker) {
            long available = portfolioHoldingRepository.findAllByUserIdAndInstrumentCode(
                    sellOrder.getUser().getId(),
                    sellOrder.getInstrumentCode()
            ).stream().mapToLong(PortfolioHolding::getQuantity).sum();
            if (available < quantity) {
                failSellOrder(sellOrder, "موجودی کافی برای اجرای سفارش فروش وجود ندارد.");
                return null;
            }
            User seller = userRepository.findByIdForUpdate(sellOrder.getUser().getId())
                    .orElse(null);
            if (seller == null) {
                failSellOrder(sellOrder, "حساب فروشنده یافت نشد.");
                return null;
            }
            sellOrder.setUser(seller);
        }

        buyOrder.setExecutedQuantity(buyOrder.getExecutedQuantity() + quantity);
        buyOrder.setRemainingQuantity(buyOrder.getRemainingQuantity() - quantity);
        buyOrder.setAverageExecutedPrice(computeAvgPrice(buyOrder));
        updateOrderStatus(buyOrder);
        tradingOrderRepository.save(buyOrder);

        sellOrder.setExecutedQuantity(sellOrder.getExecutedQuantity() + quantity);
        sellOrder.setRemainingQuantity(sellOrder.getRemainingQuantity() - quantity);
        sellOrder.setAverageExecutedPrice(computeAvgPrice(sellOrder));
        updateOrderStatus(sellOrder);
        tradingOrderRepository.save(sellOrder);

        if (!buyerIsMarketMaker) {
            User buyer = buyOrder.getUser();
            BigDecimal buyerBalance = buyer.getBalance() != null ? buyer.getBalance() : BigDecimal.ZERO;
            BigDecimal newBalance = buyerBalance.subtract(tradeValue);
            buyer.setBalance(newBalance);
            userRepository.save(buyer);
            addHolding(buyOrder.getUser().getId(), buyOrder.getSymbol(), buyOrder.getInstrumentCode(),
                    quantity, price);
            walletLedgerService.recordBalanceChange(
                    buyer,
                    tradeValue.negate(),
                    String.format("خرید %s به تعداد %d با قیمت %s ریال", buyOrder.getSymbol(), quantity, price.toPlainString())
            );
        }

        if (!sellerIsMarketMaker) {
            User seller = sellOrder.getUser();
            BigDecimal sellerBalance = seller.getBalance() != null ? seller.getBalance() : BigDecimal.ZERO;
            BigDecimal newBalance = sellerBalance.add(tradeValue);
            seller.setBalance(newBalance);
            userRepository.save(seller);
            removeHolding(sellOrder.getUser().getId(), sellOrder.getInstrumentCode(), quantity);
            walletLedgerService.recordBalanceChange(
                    seller,
                    tradeValue,
                    String.format("فروش %s به تعداد %d با قیمت %s ریال", sellOrder.getSymbol(), quantity, price.toPlainString())
            );
        }

        Trade trade = new Trade();
        trade.setBuyOrder(buyOrder);
        trade.setSellOrder(sellOrder);
        trade.setSymbol(buyOrder.getSymbol());
        trade.setInstrumentCode(buyOrder.getInstrumentCode());
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setValue(tradeValue);
        trade.setExecutedAt(Instant.now());
        trade.setBuyer(buyOrder.getUser());
        trade.setSeller(sellOrder.getUser());

        return tradeRepository.save(trade);
    }

    private void failBuyOrder(TradingOrder buyOrder, String reason) {
        buyOrder.setStatus(OrderStatus.FAILED);
        buyOrder.setRemainingQuantity(0L);
        buyOrder.setCancelledAt(Instant.now());
        tradingOrderRepository.save(buyOrder);
        log.warn("Buy order {} failed during matching: {}", buyOrder.getId(), reason);
    }

    private void failSellOrder(TradingOrder sellOrder, String reason) {
        sellOrder.setStatus(OrderStatus.FAILED);
        sellOrder.setRemainingQuantity(0L);
        sellOrder.setCancelledAt(Instant.now());
        tradingOrderRepository.save(sellOrder);
        log.warn("Sell order {} failed during matching: {}", sellOrder.getId(), reason);
    }

    private void addHolding(Long userId, String symbol, String instrumentCode,
                            long quantity, BigDecimal price) {
        List<PortfolioHolding> existing = portfolioHoldingRepository
                .findAllByUserIdAndInstrumentCode(userId, instrumentCode);

        if (!existing.isEmpty()) {
            PortfolioHolding holding = existing.get(0);
            long oldQty = holding.getQuantity();
            BigDecimal oldCost = holding.getBuyPrice().multiply(BigDecimal.valueOf(oldQty));
            BigDecimal newCost = price.multiply(BigDecimal.valueOf(quantity));
            long newQty = oldQty + quantity;
            BigDecimal avgPrice = oldCost.add(newCost)
                    .divide(BigDecimal.valueOf(newQty), 2, RoundingMode.HALF_UP);
            holding.setQuantity(newQty);
            holding.setBuyPrice(avgPrice);
            holding.setLivePrice(price);
            portfolioHoldingRepository.save(holding);
        } else {
            var user = userRepository.getReferenceById(userId);
            PortfolioHolding holding = new PortfolioHolding();
            holding.setUser(user);
            holding.setSymbol(symbol);
            holding.setInstrumentCode(instrumentCode);
            holding.setQuantity(quantity);
            holding.setBuyPrice(price);
            holding.setLivePrice(price);
            holding.setAcquiredAt(Instant.now());
            portfolioHoldingRepository.save(holding);
        }
    }

    private void removeHolding(Long userId, String instrumentCode, long quantity) {
        List<PortfolioHolding> holdings = portfolioHoldingRepository
                .findAllByUserIdAndInstrumentCode(userId, instrumentCode);

        long remaining = quantity;
        for (PortfolioHolding holding : holdings) {
            if (remaining <= 0) {
                break;
            }
            if (holding.getQuantity() <= remaining) {
                remaining -= holding.getQuantity();
                portfolioHoldingRepository.delete(holding);
            } else {
                holding.setQuantity(holding.getQuantity() - remaining);
                portfolioHoldingRepository.save(holding);
                remaining = 0;
            }
        }
    }

    private BigDecimal computeAvgPrice(TradingOrder order) {
        if (order.getExecutedQuantity() <= 0) {
            return BigDecimal.ZERO;
        }

        List<Trade> trades;
        if (order.getSide() == OrderSide.BUY) {
            trades = tradeRepository.findAllByBuyOrderIdOrSellOrderIdOrderByExecutedAtDesc(
                    order.getId(), -1L);
        } else {
            trades = tradeRepository.findAllByBuyOrderIdOrSellOrderIdOrderByExecutedAtDesc(
                    -1L, order.getId());
        }

        BigDecimal totalValue = BigDecimal.ZERO;
        long totalQty = 0;
        for (Trade trade : trades) {
            totalValue = totalValue.add(trade.getPrice().multiply(BigDecimal.valueOf(trade.getQuantity())));
            totalQty += trade.getQuantity();
        }

        if (totalQty == 0) {
            return order.getOrderPrice();
        }
        return totalValue.divide(BigDecimal.valueOf(totalQty), 2, RoundingMode.HALF_UP);
    }

    private void updateOrderStatus(TradingOrder order) {
        if (order.getRemainingQuantity() <= 0) {
            order.setStatus(OrderStatus.COMPLETED);
        } else if (order.getExecutedQuantity() > 0) {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }

    private boolean isExpired(TradingOrder order) {
        Instant expiresAt = order.getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
