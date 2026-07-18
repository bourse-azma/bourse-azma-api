package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradeRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.OrderUpdateNotifier;
import com.ernoxin.bourseazmaapi.service.WalletLedgerService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeExecutor {

    private final TradingOrderRepository tradingOrderRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final UserRepository userRepository;
    private final MarketMakerService marketMakerService;
    private final WalletLedgerService walletLedgerService;
    private final EntityManager entityManager;
    private final OrderUpdateNotifier orderUpdateNotifier;

    @Transactional
    public Trade executeTrade(TradingOrder buyOrder, TradingOrder sellOrder,
                              long quantity, BigDecimal price) {
        validateExecution(buyOrder, sellOrder, quantity, price);
        BigDecimal tradeValue = price.multiply(BigDecimal.valueOf(quantity));

        boolean buyerIsMarketMaker = marketMakerService.isMarketMaker(buyOrder.getUser());
        boolean sellerIsMarketMaker = marketMakerService.isMarketMaker(sellOrder.getUser());
        boolean sameUser = !buyerIsMarketMaker && !sellerIsMarketMaker
                && buyOrder.getUser() != null && sellOrder.getUser() != null
                && Objects.equals(buyOrder.getUser().getId(), sellOrder.getUser().getId());

        // Every transaction acquires account locks in the same global order. Without
        // this ordering, simultaneous A->B and B->A trades can deadlock.
        TreeSet<Long> participantIds = new TreeSet<>();
        if (!buyerIsMarketMaker && buyOrder.getUser() != null && buyOrder.getUser().getId() != null) {
            participantIds.add(buyOrder.getUser().getId());
        }
        if (!sellerIsMarketMaker && sellOrder.getUser() != null && sellOrder.getUser().getId() != null) {
            participantIds.add(sellOrder.getUser().getId());
        }
        Map<Long, User> lockedUsers = new HashMap<>();
        for (Long participantId : participantIds) {
            User locked = userRepository.findByIdForUpdate(participantId).orElse(null);
            if (locked == null) {
                boolean missingBuyer = !buyerIsMarketMaker && buyOrder.getUser() != null
                        && Objects.equals(participantId, buyOrder.getUser().getId());
                boolean missingSeller = !sellerIsMarketMaker && sellOrder.getUser() != null
                        && Objects.equals(participantId, sellOrder.getUser().getId());
                if (missingBuyer) {
                    failBuyOrder(buyOrder, sameUser ? "حساب کاربر یافت نشد." : "حساب خریدار یافت نشد.");
                }
                if (missingSeller) {
                    failSellOrder(sellOrder, sameUser ? "حساب کاربر یافت نشد." : "حساب فروشنده یافت نشد.");
                }
                return null;
            }
            // Orders may already have placed this User in the persistence context
            // before the lock was acquired. Refresh after waiting for the lock so
            // balance calculations never use that stale first-level-cache snapshot.
            entityManager.refresh(locked);
            lockedUsers.put(participantId, locked);
        }

        if (sameUser) {
            User user = lockedUsers.get(buyOrder.getUser().getId());
            BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
            if (balance.compareTo(tradeValue) < 0) {
                failBuyOrder(buyOrder, "موجودی کافی برای اجرای سفارش خرید وجود ندارد.");
                return null;
            }
            long available = portfolioHoldingRepository.findAllByUserIdAndInstrumentCodeForUpdate(
                    user.getId(), sellOrder.getInstrumentCode()
            ).stream().mapToLong(PortfolioHolding::getQuantity).sum();
            if (available < quantity) {
                failSellOrder(sellOrder, "موجودی کافی برای اجرای سفارش فروش وجود ندارد.");
                return null;
            }
            buyOrder.setUser(user);
            sellOrder.setUser(user);
        } else {
            if (!buyerIsMarketMaker) {
                User buyer = lockedUsers.get(buyOrder.getUser().getId());
                BigDecimal buyerBalance = buyer.getBalance() != null ? buyer.getBalance() : BigDecimal.ZERO;
                if (buyerBalance.compareTo(tradeValue) < 0) {
                    failBuyOrder(buyOrder, "موجودی کافی برای اجرای سفارش خرید وجود ندارد.");
                    return null;
                }
                buyOrder.setUser(buyer);
            }

            if (!sellerIsMarketMaker) {
                User seller = lockedUsers.get(sellOrder.getUser().getId());
                long available = portfolioHoldingRepository.findAllByUserIdAndInstrumentCodeForUpdate(
                        seller.getId(),
                        sellOrder.getInstrumentCode()
                ).stream().mapToLong(PortfolioHolding::getQuantity).sum();
                if (available < quantity) {
                    failSellOrder(sellOrder, "موجودی کافی برای اجرای سفارش فروش وجود ندارد.");
                    return null;
                }
                sellOrder.setUser(seller);
            }
        }

        applyFill(buyOrder, quantity, price);
        updateOrderStatus(buyOrder);
        tradingOrderRepository.save(buyOrder);

        applyFill(sellOrder, quantity, price);
        updateOrderStatus(sellOrder);
        tradingOrderRepository.save(sellOrder);
        orderUpdateNotifier.publish(buyOrder);
        orderUpdateNotifier.publish(sellOrder);

        if (sameUser) {
            // Wash trade: cash and inventory net to zero; ledger still records both legs.
            User user = buyOrder.getUser();
            BigDecimal originalBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
            user.setBalance(originalBalance.subtract(tradeValue));
            walletLedgerService.recordBalanceChange(
                    user,
                    tradeValue.negate(),
                    String.format("خرید %s به تعداد %d با قیمت %s ریال (معامله داخلی صف)",
                            buyOrder.getSymbol(), quantity, price.toPlainString()),
                    WalletTransactionSource.TRADE_BUY
            );
            user.setBalance(originalBalance);
            userRepository.save(user);
            walletLedgerService.recordBalanceChange(
                    user,
                    tradeValue,
                    String.format("فروش %s به تعداد %d با قیمت %s ریال (معامله داخلی صف)",
                            sellOrder.getSymbol(), quantity, price.toPlainString()),
                    WalletTransactionSource.TRADE_SELL
            );
            // A wash trade does not change inventory or its cost basis. Applying the
            // sell and buy legs separately would re-price the returned shares at the
            // execution price and let the user manipulate the portfolio average.
        } else {
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
                        String.format("خرید %s به تعداد %d با قیمت %s ریال", buyOrder.getSymbol(), quantity, price.toPlainString()),
                        WalletTransactionSource.TRADE_BUY
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
                        String.format("فروش %s به تعداد %d با قیمت %s ریال", sellOrder.getSymbol(), quantity, price.toPlainString()),
                        WalletTransactionSource.TRADE_SELL
                );
            }
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
        orderUpdateNotifier.publish(buyOrder);
        log.warn("Buy order {} failed during matching: {}", buyOrder.getId(), reason);
    }

    private void failSellOrder(TradingOrder sellOrder, String reason) {
        sellOrder.setStatus(OrderStatus.FAILED);
        sellOrder.setRemainingQuantity(0L);
        sellOrder.setCancelledAt(Instant.now());
        tradingOrderRepository.save(sellOrder);
        orderUpdateNotifier.publish(sellOrder);
        log.warn("Sell order {} failed during matching: {}", sellOrder.getId(), reason);
    }

    private void addHolding(Long userId, String symbol, String instrumentCode,
                            long quantity, BigDecimal price) {
        List<PortfolioHolding> existing = portfolioHoldingRepository
                .findAllByUserIdAndInstrumentCodeForUpdate(userId, instrumentCode);

        if (!existing.isEmpty()) {
            PortfolioHolding holding = existing.get(0);
            long oldQty = 0L;
            BigDecimal oldCost = BigDecimal.ZERO;
            for (PortfolioHolding current : existing) {
                oldQty = Math.addExact(oldQty, current.getQuantity());
                oldCost = oldCost.add(current.getBuyPrice()
                        .multiply(BigDecimal.valueOf(current.getQuantity())));
            }
            BigDecimal newCost = price.multiply(BigDecimal.valueOf(quantity));
            long newQty = Math.addExact(oldQty, quantity);
            BigDecimal avgPrice = oldCost.add(newCost)
                    .divide(BigDecimal.valueOf(newQty), 2, RoundingMode.HALF_UP);
            holding.setSymbol(symbol);
            holding.setQuantity(newQty);
            holding.setBuyPrice(avgPrice);
            holding.setLivePrice(price);
            portfolioHoldingRepository.save(holding);
            if (existing.size() > 1) {
                // Repair legacy duplicates while preserving their complete cost basis.
                portfolioHoldingRepository.deleteAll(existing.subList(1, existing.size()));
            }
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
                .findAllByUserIdAndInstrumentCodeForUpdate(userId, instrumentCode);

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
        if (remaining > 0) {
            throw new IllegalStateException(
                    "موجودی پرتفوی برای تکمیل فروش کافی نیست (userId=" + userId
                            + ", instrumentCode=" + instrumentCode + ").");
        }
    }

    private void applyFill(TradingOrder order, long fillQuantity, BigDecimal fillPrice) {
        long previousQuantity = order.getExecutedQuantity();
        BigDecimal previousAverage = order.getAverageExecutedPrice() != null
                ? order.getAverageExecutedPrice()
                : BigDecimal.ZERO;
        long newExecutedQuantity = previousQuantity + fillQuantity;
        BigDecimal totalValue = previousAverage.multiply(BigDecimal.valueOf(previousQuantity))
                .add(fillPrice.multiply(BigDecimal.valueOf(fillQuantity)));

        order.setExecutedQuantity(newExecutedQuantity);
        order.setRemainingQuantity(order.getRemainingQuantity() - fillQuantity);
        order.setAverageExecutedPrice(totalValue.divide(
                BigDecimal.valueOf(newExecutedQuantity), 2, RoundingMode.HALF_UP));
    }

    private void validateExecution(TradingOrder buyOrder, TradingOrder sellOrder,
                                   long quantity, BigDecimal price) {
        if (buyOrder == null || sellOrder == null) {
            throw new IllegalArgumentException("هر دو سفارش خرید و فروش برای اجرای معامله الزامی هستند.");
        }
        if (buyOrder.getUser() == null || buyOrder.getUser().getId() == null
                || sellOrder.getUser() == null || sellOrder.getUser().getId() == null) {
            throw new IllegalArgumentException("هر دو سفارش باید به یک حساب معتبر متصل باشند.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("تعداد معامله باید بیشتر از صفر باشد.");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("قیمت معامله باید بیشتر از صفر باشد.");
        }
        if (buyOrder.getSide() != OrderSide.BUY || sellOrder.getSide() != OrderSide.SELL) {
            throw new IllegalArgumentException("جهت سفارش‌های معامله نامعتبر است.");
        }
        if (!Objects.equals(buyOrder.getInstrumentCode(), sellOrder.getInstrumentCode())) {
            throw new IllegalArgumentException("کد ابزار سفارش خرید و فروش باید یکسان باشد.");
        }
        if (buyOrder.getRemainingQuantity() == null || sellOrder.getRemainingQuantity() == null
                || quantity > buyOrder.getRemainingQuantity() || quantity > sellOrder.getRemainingQuantity()) {
            throw new IllegalArgumentException("تعداد معامله از مانده یکی از سفارش‌ها بیشتر است.");
        }
    }

    private void updateOrderStatus(TradingOrder order) {
        if (order.getRemainingQuantity() <= 0) {
            order.setStatus(OrderStatus.COMPLETED);
        } else if (order.getExecutedQuantity() > 0) {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }
}
