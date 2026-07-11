package com.ernoxin.bourseazmaapi.service.ordermatching;

import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradeRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.service.MarketMakerService;
import com.ernoxin.bourseazmaapi.service.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

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

    public Trade executeTrade(TradingOrder buyOrder, TradingOrder sellOrder,
                              long quantity, BigDecimal price) {
        BigDecimal tradeValue = price.multiply(BigDecimal.valueOf(quantity));

        boolean buyerIsMarketMaker = marketMakerService.isMarketMaker(buyOrder.getUser());
        boolean sellerIsMarketMaker = marketMakerService.isMarketMaker(sellOrder.getUser());
        boolean sameUser = !buyerIsMarketMaker && !sellerIsMarketMaker
                && buyOrder.getUser() != null && sellOrder.getUser() != null
                && Objects.equals(buyOrder.getUser().getId(), sellOrder.getUser().getId());

        if (sameUser) {
            User user = userRepository.findByIdForUpdate(buyOrder.getUser().getId()).orElse(null);
            if (user == null) {
                failBuyOrder(buyOrder, "حساب کاربر یافت نشد.");
                failSellOrder(sellOrder, "حساب کاربر یافت نشد.");
                return null;
            }
            BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
            if (balance.compareTo(tradeValue) < 0) {
                failBuyOrder(buyOrder, "موجودی کافی برای اجرای سفارش خرید وجود ندارد.");
                return null;
            }
            long available = portfolioHoldingRepository.findAllByUserIdAndInstrumentCode(
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
                User seller = userRepository.findByIdForUpdate(sellOrder.getUser().getId())
                        .orElse(null);
                if (seller == null) {
                    failSellOrder(sellOrder, "حساب فروشنده یافت نشد.");
                    return null;
                }
                long available = portfolioHoldingRepository.findAllByUserIdAndInstrumentCode(
                        sellOrder.getUser().getId(),
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

        if (sameUser) {
            // Wash trade: cash and inventory net to zero; ledger still records both legs.
            User user = buyOrder.getUser();
            walletLedgerService.recordBalanceChange(
                    user,
                    tradeValue.negate(),
                    String.format("خرید %s به تعداد %d با قیمت %s ریال (معامله داخلی صف)",
                            buyOrder.getSymbol(), quantity, price.toPlainString())
            );
            walletLedgerService.recordBalanceChange(
                    user,
                    tradeValue,
                    String.format("فروش %s به تعداد %d با قیمت %s ریال (معامله داخلی صف)",
                            sellOrder.getSymbol(), quantity, price.toPlainString())
            );
            // Holdings: remove sold shares then add bought shares at trade price (net same qty).
            removeHolding(user.getId(), sellOrder.getInstrumentCode(), quantity);
            addHolding(user.getId(), buyOrder.getSymbol(), buyOrder.getInstrumentCode(), quantity, price);
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

    private void updateOrderStatus(TradingOrder order) {
        if (order.getRemainingQuantity() <= 0) {
            order.setStatus(OrderStatus.COMPLETED);
        } else if (order.getExecutedQuantity() > 0) {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
    }
}
