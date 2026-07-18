package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.PortfolioHoldingResponse;
import com.ernoxin.bourseazmaapi.dto.TradingOrderResponse;
import com.ernoxin.bourseazmaapi.dto.admin.*;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.mapper.WalletMapper;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.*;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {
    private static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);
    private static final int DETAIL_LIMIT = 50;

    private final UserRepository userRepository;
    private final TradingOrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final PortfolioHoldingRepository holdingRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserActivityLogRepository activityRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final WalletMapper walletMapper;
    private final MarketLiquidityService marketLiquidityService;

    public AdminDashboardStatsResponse stats() {
        Instant onlineAfter = Instant.now().minus(ONLINE_WINDOW);
        Instant today = LocalDate.now(ZoneId.of("Asia/Tehran")).atStartOfDay(ZoneId.of("Asia/Tehran")).toInstant();
        return new AdminDashboardStatsResponse(
                userRepository.countByRoleAndUsernameNotAndDeletedAtIsNull(UserRole.USER, MarketMakerService.MARKET_MAKER_USERNAME),
                userRepository.countByRoleAndUsernameNotAndDeletedAtIsNullAndBlockedFalseAndLastSeenAtAfter(UserRole.USER, MarketMakerService.MARKET_MAKER_USERNAME, onlineAfter),
                userRepository.countByRoleAndUsernameNotAndDeletedAtIsNullAndCreatedAtAfter(UserRole.USER, MarketMakerService.MARKET_MAKER_USERNAME, today),
                orderRepository.count(),
                tradeRepository.count(),
                supportRequestRepository.countByStatusIn(List.of(SupportRequestStatus.OPEN, SupportRequestStatus.IN_PROGRESS))
        );
    }

    public PagedResponse<AdminUserSummaryResponse> users(String search, boolean onlineOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Instant onlineAfter = Instant.now().minus(ONLINE_WINDOW);
        Specification<User> spec = (root, query, cb) -> cb.equal(root.get("role"), UserRole.USER);
        spec = spec.and((root, query, cb) -> cb.notEqual(root.get("username"), MarketMakerService.MARKET_MAKER_USERNAME));
        spec = spec.and((root, query, cb) -> cb.isNull(root.get("deletedAt")));
        if (search != null && !search.isBlank()) {
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), term),
                    cb.like(cb.lower(root.get("firstName")), term),
                    cb.like(cb.lower(root.get("lastName")), term),
                    cb.like(cb.lower(root.get("email")), term),
                    cb.like(root.get("phoneNumber"), term)
            ));
        }
        if (onlineOnly) {
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("lastSeenAt"), onlineAfter));
            spec = spec.and((root, query, cb) -> cb.isFalse(root.get("blocked")));
        }
        Page<User> result = userRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AdminUserSummaryResponse> items = result.getContent().stream().map(this::summary).toList();
        return new PagedResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.hasNext());
    }

    public AdminUserDetailResponse userDetail(Long userId) {
        User user = userRepository.findById(userId)
                .filter(item -> item.getRole() == UserRole.USER)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر مورد نظر یافت نشد."));
        PageRequest latest = PageRequest.of(0, DETAIL_LIMIT);
        List<TradingOrderResponse> orders = orderRepository
                .findAllByUserIdOrderByOrderTimeDesc(userId, latest).getContent().stream().map(this::order).toList();
        List<AdminTradeResponse> trades = tradeRepository.findAllByUserId(userId, latest).getContent().stream()
                .map(item -> trade(item, userId)).toList();
        List<PortfolioHoldingResponse> portfolio = holdingRepository.findAllByUserIdOrderByAcquiredAtDesc(userId)
                .stream().map(this::holding).toList();
        var wallet = walletMapper.toDtoList(walletTransactionRepository
                .findAllByUserIdOrderByCreatedAtDesc(userId, latest).getContent());
        List<AdminActivityResponse> activities = activityRepository
                .findAllByUserIdAndActivityTypeInOrderByOccurredAtDesc(
                        userId, List.of("LOGIN", "LOGOUT"), latest).getContent().stream().map(this::activity).toList();
        return new AdminUserDetailResponse(summary(user), orders, trades, portfolio, wallet, activities);
    }

    @Transactional
    public AdminUserDetailResponse updateBalance(Long userId, AdminBalanceUpdateRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .filter(item -> item.getRole() == UserRole.USER)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر مورد نظر یافت نشد."));
        User admin = userRepository.findById(SecurityUtils.currentUserId())
                .filter(item -> item.getRole() == UserRole.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("مدیر انجام‌دهنده یافت نشد."));

        BigDecimal previousBalance = user.getBalance() == null ? BigDecimal.ZERO : user.getBalance();
        BigDecimal newBalance = request.balance();
        BigDecimal difference = newBalance.subtract(previousBalance);
        user.setBalance(newBalance);
        userRepository.save(user);

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(difference);
        tx.setBalanceAfter(newBalance);
        tx.setDescription("ویرایش موجودی توسط مدیر از " + previousBalance.toPlainString()
                + " به " + newBalance.toPlainString() + " ریال");
        tx.setSource(WalletTransactionSource.ADMIN_ADJUSTMENT.name());
        tx.setPerformedByAdmin(admin);
        tx.setAdminNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        tx.setCreatedAt(Instant.now());
        walletTransactionRepository.save(tx);
        return userDetail(userId);
    }

    private AdminUserSummaryResponse summary(User user) {
        boolean online = !user.isBlocked() && user.getLastSeenAt() != null
                && user.getLastSeenAt().isAfter(Instant.now().minus(ONLINE_WINDOW));
        return new AdminUserSummaryResponse(user.getId(), user.getUsername(), user.getFirstName(), user.getLastName(),
                user.getPhoneNumber(), user.getEmail(), user.getBalance(), user.getCreatedAt(),
                user.getLastLoginAt(), user.getLastSeenAt(), user.getLastLoginIp(), user.isBlocked(),
                user.getBlockedAt(), user.getBlockedReason(), online,
                orderRepository.countByUserId(user.getId()), tradeRepository.countByUserId(user.getId()),
                holdingRepository.countByUserId(user.getId()), supportRequestRepository.countByUserId(user.getId()));
    }

    private TradingOrderResponse order(TradingOrder o) {
        var value = o.getOrderPrice().multiply(java.math.BigDecimal.valueOf(o.getQuantity()));
        String side = o.getSide() == OrderSide.BUY ? "خرید" : "فروش";
        String status = switch (o.getStatus()) {
            case REQUESTED -> "درخواست شده";
            case PARTIALLY_FILLED -> "اجرای جزئی";
            case COMPLETED -> "انجام شده";
            case CANCELLED -> "لغو شده";
            case FAILED -> "ناموفق";
            case TRIGGER_PENDING -> "در انتظار شرط";
        };
        return new TradingOrderResponse(o.getId(), o.getSide(), side, o.getSymbol(), o.getInstrumentCode(),
                o.getQuantity(), o.getRemainingQuantity(), o.getExecutedQuantity(), o.getOrderPrice(), o.getLivePrice(),
                o.getAverageExecutedPrice(), value, o.getOrderTime(), o.getCancelledAt(), o.getStatus(), status,
                o.isCancellable(), o.getOrderType(), o.getOrderType() == OrderType.CONDITIONAL ? "سفارش شرطی" : "سفارش عادی",
                o.getPriceType(), o.getTriggerComparator(), o.getTriggerPrice());
    }

    private PortfolioHoldingResponse holding(PortfolioHolding h) {
        var livePrice = marketLiquidityService.getReferencePrice(h.getInstrumentCode()).orElse(h.getLivePrice());
        return new PortfolioHoldingResponse(h.getId(), h.getAcquiredAt(), h.getSymbol(), h.getInstrumentCode(),
                h.getQuantity(), h.getBuyPrice(), livePrice,
                livePrice.multiply(java.math.BigDecimal.valueOf(h.getQuantity())));
    }

    private AdminTradeResponse trade(Trade t, Long userId) {
        OrderSide side = t.getBuyer() != null && userId.equals(t.getBuyer().getId()) ? OrderSide.BUY : OrderSide.SELL;
        return new AdminTradeResponse(t.getId(), t.getSymbol(), t.getInstrumentCode(), side, t.getQuantity(),
                t.getPrice(), t.getValue(), t.getExecutedAt());
    }

    private AdminActivityResponse activity(UserActivityLog log) {
        return new AdminActivityResponse(log.getId(), log.getActivityType(), log.getIpAddress(), log.getOccurredAt());
    }
}
