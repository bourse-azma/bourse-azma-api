package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.*;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
                .map(responseMapper::toPortfolioResponse)
                .toList();
    }

    @Override
    @Transactional
    public CreateOrderResult createOrder(Long userId, CreateTradingOrderRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر یافت نشد."));

        String instrumentCode = request.getInstrumentCode().trim();
        validateMarketOpen();

        BigDecimal livePrice = resolveLivePrice(request, instrumentCode);
        BigDecimal effectivePrice = resolveEffectivePrice(request, instrumentCode);
        long quantity = request.getQuantity();
        BigDecimal orderValue = effectivePrice.multiply(BigDecimal.valueOf(quantity));

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
        if (!isConditional) {
            trades = orderMatchingService.matchOrder(saved);
            // Refresh entity after matching
            saved = tradingOrderRepository.findById(saved.getId()).orElse(saved);
        }

        // For MARKET orders: if not fully filled, cancel remaining quantity explicitly.
        if (!isConditional && request.getPriceType() == PriceType.MARKET
                && saved.getRemainingQuantity() > 0 && saved.getStatus() != OrderStatus.COMPLETED) {
            saved.setRemainingQuantity(0L);
            saved.setCancelledAt(Instant.now());
            if (saved.getExecutedQuantity() > 0) {
                saved.setStatus(OrderStatus.PARTIALLY_FILLED);
            } else {
                saved.setStatus(OrderStatus.FAILED);
            }
            tradingOrderRepository.save(saved);
        }

        List<TradeResponse> tradeResponses = trades.stream()
                .map(t -> new TradeResponse(t.getId(), t.getQuantity(), t.getPrice(), t.getValue(), t.getExecutedAt()))
                .toList();

        return new CreateOrderResult(responseMapper.toOrderResponse(saved), tradeResponses);
    }

    @Override
    @Transactional
    public CancelOrderResult cancelOrder(Long userId, Long orderId) {
        TradingOrder order = tradingOrderRepository.findByIdAndUserId(orderId, userId)
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

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setRemainingQuantity(0L);

        tradingOrderRepository.save(order);

        // After cancellation, re-run matching in case freed liquidity enables other matches
        orderMatchingService.runMatchingForInstrument(order.getInstrumentCode());

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

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("کاربر یافت نشد.");
        }
    }

}
