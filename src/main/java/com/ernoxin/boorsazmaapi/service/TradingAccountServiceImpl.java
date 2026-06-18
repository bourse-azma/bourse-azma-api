package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.PortfolioHoldingResponse;
import com.ernoxin.boorsazmaapi.dto.TradingOrderResponse;
import com.ernoxin.boorsazmaapi.model.OrderSide;
import com.ernoxin.boorsazmaapi.model.OrderStatus;
import com.ernoxin.boorsazmaapi.model.PortfolioHolding;
import com.ernoxin.boorsazmaapi.model.TradingOrder;
import com.ernoxin.boorsazmaapi.repository.PortfolioHoldingRepository;
import com.ernoxin.boorsazmaapi.repository.TradingOrderRepository;
import com.ernoxin.boorsazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingAccountServiceImpl implements TradingAccountService {

    private final TradingOrderRepository tradingOrderRepository;
    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final UserRepository userRepository;

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

    private void ensureUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("کاربر یافت نشد.");
        }
    }

    private TradingOrderResponse toOrderResponse(TradingOrder order) {
        return new TradingOrderResponse(
                order.getId(),
                order.getSide(),
                sideLabel(order.getSide()),
                order.getSymbol(),
                order.getInstrumentCode(),
                order.getQuantity(),
                order.getOrderPrice(),
                order.getLivePrice(),
                order.getOrderTime(),
                order.getStatus(),
                statusLabel(order.getStatus())
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

    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case REQUESTED -> "درخواست شده";
            case COMPLETED -> "انجام شده";
            case FAILED -> "ناموفق";
        };
    }
}
