package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradingAccountServiceImpl implements TradingAccountService {

    private static final String ORDER_BOOK_PRICE_ERROR =
            "این سایت در حالت دمو است؛ قیمت سفارش باید در محدوده صف خرید یا فروش باشد و ثبت قیمت خارج از این بازه امکان‌پذیر نیست.";
    private static final String ORDER_BOOK_UNAVAILABLE_ERROR =
            "اطلاعات صف خرید و فروش در دسترس نیست؛ امکان ثبت سفارش وجود ندارد.";
    private final TradingOrderRepository tradingOrderRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final UserRepository userRepository;
    private final OrderMatchingService orderMatchingService;
    private final MarketLiquidityService marketLiquidityService;

    @Override
    @Transactional(readOnly = true)
    public List<TradingOrderResponse> getOrders(Long userId) {
        ensureUserExists(userId);
        return tradingOrderRepository.findAllByUserIdOrderByOrderTimeDesc(userId).stream()
                .map(this::toOrderResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioHoldingResponse> getPortfolio(Long userId) {
        ensureUserExists(userId);
        return portfolioHoldingRepository.findAllByUserIdOrderByAcquiredAtDesc(userId).stream()
                .map(this::toPortfolioResponse)
                .toList();
    }

    @Override
    @Transactional
    public CreateOrderResult createOrder(Long userId, CreateTradingOrderRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر یافت نشد."));

        String instrumentCode = request.getInstrumentCode().trim();
        validateOrderBookReady(instrumentCode);

        BigDecimal livePrice = scaled(request.getLivePrice());
        BigDecimal effectivePrice = resolveEffectivePrice(request, livePrice);
        long quantity = request.getQuantity();
        BigDecimal orderValue = effectivePrice.multiply(BigDecimal.valueOf(quantity));

        if (request.getOrderType() == OrderType.CONDITIONAL) {
            validateTrigger(request);
        }

        if (request.getPriceType() == PriceType.CUSTOM) {
            validateCustomPriceWithinOrderBook(request.getSide(), instrumentCode, effectivePrice);
        }

        if (request.getSide() == OrderSide.BUY) {
            validateBuyingPower(user, orderValue);
        } else {
            validateHoldingsForSell(userId, request.getInstrumentCode(), quantity);
        }

        boolean isConditional = request.getOrderType() == OrderType.CONDITIONAL;

        TradingOrder order = new TradingOrder();
        order.setUser(user);
        order.setSide(request.getSide());
        order.setOrderType(request.getOrderType());
        order.setPriceType(request.getPriceType());
        order.setSymbol(request.getSymbol().trim());
        order.setInstrumentCode(request.getInstrumentCode().trim());
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);
        order.setExecutedQuantity(0L);
        order.setOrderPrice(effectivePrice);
        order.setLivePrice(livePrice);
        order.setOrderTime(Instant.now());
        order.setStatus(isConditional ? OrderStatus.TRIGGER_PENDING : OrderStatus.REQUESTED);
        order.setValidity(request.getValidityType());
        order.setExpiresAt(resolveExpiry(request.getValidityType()));

        if (isConditional && request.getTrigger() != null) {
            order.setTriggerComparator(request.getTrigger().getComparator());
            order.setTriggerPrice(scaled(request.getTrigger().getPrice()));
        }

        TradingOrder saved = tradingOrderRepository.save(order);

        List<Trade> trades = List.of();
        if (!isConditional) {
            trades = orderMatchingService.matchOrder(saved);
            // Refresh entity after matching
            saved = tradingOrderRepository.findById(saved.getId()).orElse(saved);
        }

        // For MARKET orders: if not fully filled, cancel remaining
        if (!isConditional && request.getPriceType() == PriceType.MARKET
                && saved.getRemainingQuantity() > 0 && saved.getStatus() != OrderStatus.COMPLETED) {
            if (saved.getExecutedQuantity() > 0) {
                saved.setStatus(OrderStatus.PARTIALLY_FILLED);
            }
            // Cancel remaining for market orders that couldn't be fully filled
            saved.setRemainingQuantity(0L);
            saved.setStatus(saved.getExecutedQuantity() > 0 ? OrderStatus.COMPLETED : OrderStatus.FAILED);
            saved.setCancelledAt(Instant.now());
            tradingOrderRepository.save(saved);

            // Release reserved cash/holdings for the unfilled portion
            // (The matching engine already handled the filled portion)
        }

        List<TradeResponse> tradeResponses = trades.stream()
                .map(t -> new TradeResponse(t.getId(), t.getQuantity(), t.getPrice(), t.getValue(), t.getExecutedAt()))
                .toList();

        return new CreateOrderResult(toOrderResponse(saved), tradeResponses);
    }

    @Override
    @Transactional
    public CancelOrderResult cancelOrder(Long userId, Long orderId) {
        TradingOrder order = tradingOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("سفارش یافت نشد."));

        if (!order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("شما مجاز به لغو این سفارش نیستید.");
        }

        if (!order.isCancellable()) {
            String reason = switch (order.getStatus()) {
                case COMPLETED -> "سفارش قبلاً اجرا شده و قابل لغو نیست.";
                case CANCELLED -> "سفارش قبلاً لغو شده است.";
                case FAILED -> "سفارش ناموفق بوده و قابل لغو نیست.";
                default -> "سفارش در وضعیت فعلی قابل لغو نیست.";
            };
            throw new IllegalArgumentException(reason);
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setRemainingQuantity(0L);

        tradingOrderRepository.save(order);

        // After cancellation, re-run matching in case freed liquidity enables other matches
        orderMatchingService.runMatchingForInstrument(order.getInstrumentCode());

        return new CancelOrderResult(toOrderResponse(order));
    }

    private BigDecimal resolveEffectivePrice(CreateTradingOrderRequest request, BigDecimal livePrice) {
        if (request.getPriceType() == PriceType.MARKET) {
            return livePrice;
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("برای قیمت دلخواه باید قیمت معتبری وارد شود.");
        }
        return scaled(request.getPrice());
    }

    private void validateTrigger(CreateTradingOrderRequest request) {
        CreateTradingOrderRequest.TriggerRequest trigger = request.getTrigger();
        if (trigger == null || trigger.getComparator() == null
                || trigger.getPrice() == null || trigger.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("برای سفارش شرطی باید شرط قیمت و قیمت معتبر مشخص شود.");
        }
    }

    private void validateOrderBookReady(String instrumentCode) {
        if (!marketLiquidityService.isOrderBookReady(instrumentCode)) {
            throw new IllegalArgumentException(ORDER_BOOK_UNAVAILABLE_ERROR);
        }
    }

    private void validateCustomPriceWithinOrderBook(OrderSide side, String instrumentCode, BigDecimal price) {
        Optional<OrderBookPriceRange> range = side == OrderSide.BUY
                ? marketLiquidityService.getBidPriceRange(instrumentCode)
                : marketLiquidityService.getAskPriceRange(instrumentCode);
        OrderBookPriceRange bounds = range.orElseThrow(
                () -> new IllegalArgumentException(ORDER_BOOK_UNAVAILABLE_ERROR));
        if (price.compareTo(bounds.min()) < 0 || price.compareTo(bounds.max()) > 0) {
            throw new IllegalArgumentException(ORDER_BOOK_PRICE_ERROR);
        }
    }

    private void validateBuyingPower(User user, BigDecimal orderValue) {
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal committed = tradingOrderRepository.sumReservedBuyValue(user.getId());
        BigDecimal buyingPower = balance.subtract(committed).max(BigDecimal.ZERO);
        if (orderValue.compareTo(buyingPower) > 0) {
            throw new IllegalArgumentException("ارزش سفارش از قدرت خرید شما بیشتر است.");
        }
    }

    private void validateHoldingsForSell(Long userId, String instrumentCode, long quantity) {
        String normalizedCode = instrumentCode.trim();
        long held = portfolioHoldingRepository.findAllByUserIdAndInstrumentCode(userId, normalizedCode).stream()
                .mapToLong(PortfolioHolding::getQuantity)
                .sum();
        if (held <= 0) {
            throw new IllegalArgumentException("شما دارایی قابل فروشی برای این نماد ندارید.");
        }
        long reserved = tradingOrderRepository.sumReservedSellQuantity(userId, normalizedCode);
        long available = held - reserved;
        if (quantity > available) {
            throw new IllegalArgumentException(
                    "تعداد فروش از موجودی قابل فروش (" + Math.max(available, 0) + ") بیشتر است.");
        }
    }

    private Instant resolveExpiry(OrderValidity validity) {
        Instant now = Instant.now();
        return switch (validity) {
            case TODAY, DAY -> now.plus(Duration.ofDays(1));
            case DAYS_30 -> now.plus(Duration.ofDays(30));
            case DAYS_90 -> now.plus(Duration.ofDays(90));
        };
    }

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("کاربر یافت نشد.");
        }
    }

    TradingOrderResponse toOrderResponse(TradingOrder order) {
        BigDecimal orderValue = order.getOrderPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
        return new TradingOrderResponse(
                order.getId(),
                order.getSide(),
                sideLabel(order.getSide()),
                order.getSymbol(),
                order.getInstrumentCode(),
                order.getQuantity(),
                order.getRemainingQuantity(),
                order.getExecutedQuantity(),
                order.getOrderPrice(),
                order.getLivePrice(),
                order.getAverageExecutedPrice(),
                orderValue,
                order.getOrderTime(),
                order.getCancelledAt(),
                order.getStatus(),
                statusLabel(order.getStatus()),
                order.isCancellable(),
                order.getOrderType(),
                orderTypeLabel(order.getOrderType()),
                order.getPriceType(),
                order.getValidity(),
                order.getExpiresAt(),
                order.getTriggerComparator(),
                order.getTriggerPrice()
        );
    }

    private PortfolioHoldingResponse toPortfolioResponse(PortfolioHolding holding) {
        BigDecimal netValue = holding.getLivePrice().multiply(BigDecimal.valueOf(holding.getQuantity()));
        return new PortfolioHoldingResponse(
                holding.getId(),
                holding.getAcquiredAt(),
                holding.getSymbol(),
                holding.getInstrumentCode(),
                holding.getQuantity(),
                holding.getBuyPrice(),
                holding.getLivePrice(),
                netValue
        );
    }

    private String sideLabel(OrderSide side) {
        return switch (side) {
            case BUY -> "خرید";
            case SELL -> "فروش";
        };
    }

    private String orderTypeLabel(OrderType orderType) {
        if (orderType == null) {
            return "سفارش عادی";
        }
        return switch (orderType) {
            case NORMAL -> "سفارش عادی";
            case CONDITIONAL -> "سفارش شرطی";
        };
    }

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case REQUESTED -> "درخواست شده";
            case PARTIALLY_FILLED -> "اجرای جزئی";
            case COMPLETED -> "انجام شده";
            case CANCELLED -> "لغو شده";
            case FAILED -> "ناموفق";
            case TRIGGER_PENDING -> "در انتظار شرط";
        };
    }
}
