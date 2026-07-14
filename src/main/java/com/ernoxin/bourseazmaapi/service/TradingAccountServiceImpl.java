package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.config.TradingRulesProperties;
import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingAccountServiceImpl implements TradingAccountService {

    private static final String MARKET_CLOSED_ERROR = "بازار بسته است و امکان ثبت سفارش وجود ندارد.";
    private final TradingOrderRepository tradingOrderRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final UserRepository userRepository;
    private final OrderMatchingService orderMatchingService;
    private final MarketLiquidityService marketLiquidityService;
    private final MarketStateService marketStateService;
    private final TradingAccountResponseMapper responseMapper;
    private final PrivateOrderBookService privateOrderBookService;
    private final TradingRulesProperties tradingRules;

    @Value("${app.ui-debug-mode:false}")
    private boolean uiDebugMode;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<TradingOrderResponse> getOrders(Long userId, int page, int size, List<OrderStatus> statuses) {
        ensureUserExists(userId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<TradingOrder> result = statuses == null || statuses.isEmpty()
                ? tradingOrderRepository.findAllByUserIdOrderByOrderTimeDesc(userId, pageable)
                : tradingOrderRepository.findAllByUserIdAndStatusInOrderByOrderTimeDesc(userId, statuses, pageable);
        return new PagedResponse<>(
                result.getContent().stream().map(responseMapper::toOrderResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PortfolioHoldingResponse> getPortfolio(Long userId) {
        ensureUserExists(userId);
        return portfolioHoldingRepository.findAllByUserIdOrderByAcquiredAtDesc(userId).stream()
                .map(holding -> responseMapper.toPortfolioResponse(holding,
                        marketLiquidityService.getReferencePrice(holding.getInstrumentCode())
                                .orElse(holding.getLivePrice())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PrivateOrderBookResponse getOrderBook(Long userId, String instrumentCode) {
        ensureUserExists(userId);
        return privateOrderBookService.getOrderBook(userId, instrumentCode);
    }

    @Override
    public TradingRulesResponse getTradingRules() {
        return new TradingRulesResponse(
                tradingRules.minimumOrderValue(),
                tradingRules.maximumWalletAdjustment()
        );
    }

    @Override
    @Transactional
    public CreateOrderResult createOrder(Long userId, CreateTradingOrderRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر یافت نشد."));

        String instrumentCode = request.getInstrumentCode().trim();
        if (!uiDebugMode) {
            validateMarketOpen();
        }

        BigDecimal livePrice = resolveLivePrice(request, instrumentCode);
        BigDecimal effectivePrice = resolveEffectivePrice(request, instrumentCode);
        long quantity = request.getQuantity();
        BigDecimal orderValue = effectivePrice.multiply(BigDecimal.valueOf(quantity));

        if (orderValue.compareTo(tradingRules.minimumOrderValue()) < 0) {
            throw new IllegalArgumentException(
                    "حداقل ارزش هر سفارش " + formatAmount(tradingRules.minimumOrderValue()) + " ریال است."
            );
        }

        if (request.getOrderType() == OrderType.CONDITIONAL) {
            validateTrigger(request);
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

        if (isConditional && request.getTrigger() != null) {
            order.setTriggerComparator(request.getTrigger().getComparator());
            order.setTriggerPrice(scaled(request.getTrigger().getPrice()));
        }

        TradingOrder saved = tradingOrderRepository.save(order);

        List<Trade> trades = List.of();
        // Debug mode only permits submitting an order while the market is closed.
        // Execution must still follow the real market state; the scheduler will pick
        // this order up after the market opens.
        if (!isConditional && marketStateService.isMarketOpen()) {
            trades = orderMatchingService.matchOrder(saved);
            // Refresh entity after matching
            saved = tradingOrderRepository.findById(saved.getId()).orElse(saved);
        }

        List<TradeResponse> tradeResponses = trades.stream()
                .map(t -> new TradeResponse(t.getId(), t.getQuantity(), t.getPrice(), t.getValue(), t.getExecutedAt()))
                .toList();

        return new CreateOrderResult(responseMapper.toOrderResponse(saved), tradeResponses);
    }

    @Override
    @Transactional
    public UpdateOrderResult updateOrder(Long userId, Long orderId, UpdateTradingOrderRequest request) {
        // Account mutations consistently lock the user before orders/holdings. This serializes
        // one simulated account and avoids lock-order inversion with matching and order creation.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر یافت نشد."));
        TradingOrder order = tradingOrderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("سفارش یافت نشد."));

        if (!order.isCancellable()) {
            throw new IllegalArgumentException(nonEditableOrderReason(order));
        }
        if (!uiDebugMode) {
            validateMarketOpen();
        }

        long newTotalQuantity = request.getQuantity();
        long executedQuantity = order.getExecutedQuantity() != null ? order.getExecutedQuantity() : 0L;
        if (newTotalQuantity <= executedQuantity) {
            throw new IllegalArgumentException(
                    "تعداد کل جدید باید از تعداد اجراشده (" + executedQuantity + ") بیشتر باشد."
            );
        }

        BigDecimal newPrice = resolveUpdatedPrice(order, request);
        long newRemainingQuantity = newTotalQuantity - executedQuantity;
        BigDecimal remainingValue = newPrice.multiply(BigDecimal.valueOf(newRemainingQuantity));
        if (remainingValue.compareTo(tradingRules.minimumOrderValue()) < 0) {
            throw new IllegalArgumentException(
                    "ارزش باقیمانده سفارش باید حداقل "
                            + formatAmount(tradingRules.minimumOrderValue()) + " ریال باشد."
            );
        }

        if (order.getSide() == OrderSide.BUY) {
            validateBuyingPowerForUpdate(user, order.getId(), remainingValue);
        } else {
            validateHoldingsForSellUpdate(
                    userId,
                    order.getInstrumentCode(),
                    newRemainingQuantity,
                    order.getId()
            );
        }

        order.setUser(user);
        order.setQuantity(newTotalQuantity);
        order.setRemainingQuantity(newRemainingQuantity);
        order.setOrderPrice(newPrice);
        // A modified order loses its previous price-time priority, as on real order books.
        order.setOrderTime(Instant.now());
        TradingOrder saved = tradingOrderRepository.saveAndFlush(order);

        List<Trade> trades = List.of();
        if (saved.isActive() && marketStateService.isMarketOpen()) {
            trades = orderMatchingService.matchOrder(saved);
            saved = tradingOrderRepository.findById(saved.getId()).orElse(saved);
        }

        List<TradeResponse> tradeResponses = trades.stream()
                .map(t -> new TradeResponse(t.getId(), t.getQuantity(), t.getPrice(), t.getValue(), t.getExecutedAt()))
                .toList();
        return new UpdateOrderResult(responseMapper.toOrderResponse(saved), tradeResponses);
    }

    @Override
    @Transactional
    public CancelOrderResult cancelOrder(Long userId, Long orderId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر یافت نشد."));
        TradingOrder order = tradingOrderRepository.findByIdAndUserIdForUpdate(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("سفارش یافت نشد."));

        if (!order.isCancellable()) {
            String reason = switch (order.getStatus()) {
                case COMPLETED -> "سفارش قبلاً اجرا شده و قابل لغو نیست.";
                case CANCELLED -> "سفارش قبلاً لغو شده است.";
                case FAILED -> "سفارش ناموفق بوده و قابل لغو نیست.";
                default -> "سفارش در وضعیت فعلی قابل لغو نیست.";
            };
            throw new IllegalArgumentException(reason);
        }

        String instrumentCode = order.getInstrumentCode();
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setRemainingQuantity(0L);

        tradingOrderRepository.save(order);

        // Freeing reserved shares/cash can allow remaining private-book orders to match.
        orderMatchingService.runMatchingForUserInstrument(userId, instrumentCode);

        return new CancelOrderResult(responseMapper.toOrderResponse(order));
    }

    private BigDecimal resolveLivePrice(CreateTradingOrderRequest request, String instrumentCode) {
        if (request.getPriceType() == PriceType.MARKET) {
            return marketLiquidityService.resolveMarketOrderPrice(instrumentCode, request.getSide());
        }
        return scaled(request.getLivePrice());
    }

    private BigDecimal resolveEffectivePrice(CreateTradingOrderRequest request, String instrumentCode) {
        if (request.getPriceType() == PriceType.MARKET) {
            return marketLiquidityService.resolveMarketOrderPrice(instrumentCode, request.getSide());
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("برای قیمت دلخواه باید قیمت معتبری وارد شود.");
        }
        return scaled(request.getPrice());
    }

    private BigDecimal resolveUpdatedPrice(TradingOrder order, UpdateTradingOrderRequest request) {
        if (order.getPriceType() == PriceType.MARKET) {
            return marketLiquidityService.resolveMarketOrderPrice(order.getInstrumentCode(), order.getSide());
        }
        if (request.getPrice() == null || request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("برای قیمت دلخواه باید قیمت معتبری وارد شود.");
        }
        return scaled(request.getPrice());
    }

    private void validateMarketOpen() {
        if (!marketStateService.isMarketOpen()) {
            throw new IllegalArgumentException(MARKET_CLOSED_ERROR);
        }
    }

    private void validateTrigger(CreateTradingOrderRequest request) {
        CreateTradingOrderRequest.TriggerRequest trigger = request.getTrigger();
        if (trigger == null || trigger.getComparator() == null
                || trigger.getPrice() == null || trigger.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("برای سفارش شرطی باید شرط قیمت و قیمت معتبر مشخص شود.");
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

    private void validateBuyingPowerForUpdate(User user, Long orderId, BigDecimal remainingValue) {
        BigDecimal balance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal committed = tradingOrderRepository.sumReservedBuyValueExcluding(user.getId(), orderId);
        BigDecimal buyingPower = balance.subtract(committed).max(BigDecimal.ZERO);
        if (remainingValue.compareTo(buyingPower) > 0) {
            throw new IllegalArgumentException("ارزش باقیمانده سفارش از قدرت خرید شما بیشتر است.");
        }
    }

    private void validateHoldingsForSell(Long userId, String instrumentCode, long quantity) {
        String normalizedCode = instrumentCode.trim();
        long held = portfolioHoldingRepository
                .findAllByUserIdAndInstrumentCodeForUpdate(userId, normalizedCode).stream()
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

    private void validateHoldingsForSellUpdate(Long userId, String instrumentCode, long quantity, Long orderId) {
        String normalizedCode = instrumentCode.trim();
        long held = portfolioHoldingRepository
                .findAllByUserIdAndInstrumentCodeForUpdate(userId, normalizedCode)
                .stream()
                .mapToLong(PortfolioHolding::getQuantity)
                .sum();
        long reservedByOtherOrders = tradingOrderRepository.sumReservedSellQuantityExcluding(
                userId, normalizedCode, orderId
        );
        long available = held - reservedByOtherOrders;
        if (quantity > available) {
            throw new IllegalArgumentException(
                    "تعداد باقیمانده فروش از موجودی قابل فروش ("
                            + Math.max(available, 0) + ") بیشتر است."
            );
        }
    }

    private String nonEditableOrderReason(TradingOrder order) {
        return switch (order.getStatus()) {
            case COMPLETED -> "سفارش اجراشده قابل ویرایش نیست.";
            case CANCELLED -> "سفارش لغوشده قابل ویرایش نیست.";
            case FAILED -> "سفارش ناموفق قابل ویرایش نیست.";
            default -> "سفارش در وضعیت فعلی قابل ویرایش نیست.";
        };
    }

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatAmount(BigDecimal amount) {
        return String.format("%,.0f", amount);
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("کاربر یافت نشد.");
        }
    }

}
