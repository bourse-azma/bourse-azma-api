package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserCreateRequest;
import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.UserSelfUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.UserUpdateRequest;
import com.ernoxin.bourseazmaapi.dto.auth.RegisterRequest;
import com.ernoxin.bourseazmaapi.exception.DuplicateResourceException;
import com.ernoxin.bourseazmaapi.exception.InvalidCurrentPasswordException;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.mapper.UserMapper;
import com.ernoxin.bourseazmaapi.model.*;
import com.ernoxin.bourseazmaapi.repository.TradingOrderRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import com.ernoxin.bourseazmaapi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final List<OrderStatus> CANCELLABLE_ORDER_STATUSES = List.of(
            OrderStatus.REQUESTED,
            OrderStatus.PARTIALLY_FILLED,
            OrderStatus.TRIGGER_PENDING
    );
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final TradingOrderRepository tradingOrderRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        UserCreateRequest createRequest = new UserCreateRequest();
        createRequest.setUsername(request.getUsername());
        createRequest.setFirstName(request.getFirstName());
        createRequest.setLastName(request.getLastName());
        createRequest.setPhoneNumber(request.getPhoneNumber());
        createRequest.setEmail(request.getEmail());
        createRequest.setPassword(request.getPassword());
        createRequest.setBalance(resolveRegistrationBalance(request.getBalance()));
        return create(createRequest);
    }

    private BigDecimal resolveRegistrationBalance(BigDecimal balance) {
        return balance != null ? balance : BigDecimal.ZERO;
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        normalizeRequestForPersistence(request);
        validateUniqueFieldsForCreate(request);
        User user = userMapper.toEntity(request);
        user.setRole(UserRole.USER);

        BigDecimal initialBalance = request.getBalance() != null ? request.getBalance() : BigDecimal.ZERO;
        user.setBalance(initialBalance);

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        User savedUser = userRepository.save(user);

        WalletTransaction initialTx = new WalletTransaction();
        initialTx.setUser(savedUser);
        initialTx.setAmount(initialBalance);
        initialTx.setBalanceAfter(initialBalance);
        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
            initialTx.setDescription("موجودی اولیه به مبلغ " + initialBalance.toPlainString() + " ریال هنگام ثبت‌نام");
        } else {
            initialTx.setDescription("ثبت‌نام بدون موجودی اولیه");
        }
        initialTx.setCreatedAt(Instant.now());
        walletTransactionRepository.save(initialTx);

        return userMapper.toDto(savedUser);
    }

    @Override
    public UserResponse getById(Long id) {
        if (SecurityUtils.currentUserRole() != UserRole.ADMIN && !SecurityUtils.currentUserId().equals(id)) {
            throw new ResourceNotFoundException("کاربر مورد نظر یافت نشد.");
        }
        return userMapper.toDto(findById(id));
    }

    @Override
    public UserResponse getCurrentUser() {
        return userMapper.toDto(findById(SecurityUtils.currentUserId()));
    }

    @Override
    public List<UserResponse> getAll() {
        return userMapper.toDtoList(userRepository.findAll());
    }

    @Override
    public UserResponse update(UserUpdateRequest request) {
        validateOwnerOrAdmin(request.getId());
        normalizeRequestForPersistence(request);
        User user = findById(request.getId());
        validateUniqueFieldsForUpdate(request.getId(), request.getUsername(),
                request.getPhoneNumber(), request.getEmail());
        userMapper.updateEntity(request, user);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            validatePasswordChange(user, request);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        return userMapper.toDto(userRepository.save(user));
    }

    @Override
    public UserResponse updateCurrentUser(UserSelfUpdateRequest request) {
        Long userId = SecurityUtils.currentUserId();
        normalizeRequestForPersistence(request);
        User user = findById(userId);
        validateUniqueFieldsForUpdate(userId, request.getUsername(),
                request.getPhoneNumber(), request.getEmail());
        userMapper.updateEntity(request, user);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            UserUpdateRequest passwordRequest = new UserUpdateRequest();
            passwordRequest.setCurrentPassword(request.getCurrentPassword());
            passwordRequest.setPassword(request.getPassword());
            validatePasswordChange(user, passwordRequest);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        return userMapper.toDto(userRepository.save(user));
    }

    private void validatePasswordChange(User user, UserUpdateRequest request) {
        if (!SecurityUtils.currentUserId().equals(user.getId())) {
            return;
        }

        if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new InvalidCurrentPasswordException();
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        validateOwnerOrAdmin(id);
        User user = findById(id);
        cancelActiveOrdersForUser(id);
        user.setBlocked(true);
        user.setBlockedAt(Instant.now());
        user.setBlockedReason("حساب توسط مدیر حذف شده است.");
        user.setDeletedAt(Instant.now());
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUsername("deleted_" + user.getId() + "_" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(null);
        user.setPhoneNumber(null);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        userRepository.save(user);
    }

    private void cancelActiveOrdersForUser(Long userId) {
        List<TradingOrder> activeOrders =
                tradingOrderRepository.findAllByUserIdAndStatusIn(userId, CANCELLABLE_ORDER_STATUSES);
        if (activeOrders.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (TradingOrder order : activeOrders) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(now);
            order.setRemainingQuantity(0L);
        }
        tradingOrderRepository.saveAll(activeOrders);
    }

    @Override
    @Transactional
    public UserResponse setBlocked(Long id, boolean blocked, String reason) {
        User user = findById(id);
        if (user.getRole() != UserRole.USER || user.getDeletedAt() != null) {
            throw new ResourceNotFoundException("کاربر مورد نظر یافت نشد.");
        }
        user.setBlocked(blocked);
        user.setBlockedAt(blocked ? Instant.now() : null);
        String normalizedReason = reason == null ? null : reason.trim();
        user.setBlockedReason(blocked && normalizedReason != null && !normalizedReason.isBlank()
                ? normalizedReason : null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        if (blocked) {
            cancelActiveOrdersForUser(id);
        }
        return userMapper.toDto(userRepository.save(user));
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر مورد نظر یافت نشد."));
    }

    private void validateUniqueFieldsForCreate(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("نام کاربری واردشده قبلا ثبت شده است.");
        }
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("شماره موبایل واردشده قبلا ثبت شده است.");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("ایمیل واردشده قبلا ثبت شده است.");
        }
    }

    private void validateUniqueFieldsForUpdate(
            Long userId,
            String username,
            String phoneNumber,
            String email
    ) {
        if (userRepository.existsByUsernameAndIdNot(username, userId)) {
            throw new DuplicateResourceException("نام کاربری واردشده قبلا ثبت شده است.");
        }
        if (phoneNumber != null && userRepository.existsByPhoneNumberAndIdNot(phoneNumber, userId)) {
            throw new DuplicateResourceException("شماره موبایل واردشده قبلا ثبت شده است.");
        }
        if (email != null && userRepository.existsByEmailAndIdNot(email, userId)) {
            throw new DuplicateResourceException("ایمیل واردشده قبلا ثبت شده است.");
        }
    }

    private void normalizeRequestForPersistence(UserCreateRequest request) {
        request.setUsername(normalizeUsername(request.getUsername()));
        request.setFirstName(normalizeRequired(request.getFirstName()));
        request.setLastName(normalizeRequired(request.getLastName()));
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
    }

    private void normalizeRequestForPersistence(UserUpdateRequest request) {
        request.setUsername(normalizeUsername(request.getUsername()));
        request.setFirstName(normalizeRequired(request.getFirstName()));
        request.setLastName(normalizeRequired(request.getLastName()));
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
    }

    private void normalizeRequestForPersistence(UserSelfUpdateRequest request) {
        request.setUsername(normalizeUsername(request.getUsername()));
        request.setFirstName(normalizeRequired(request.getFirstName()));
        request.setLastName(normalizeRequired(request.getLastName()));
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
    }

    private String normalizeUsername(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void validateOwnerOrAdmin(Long targetUserId) {
        Long currentUserId = SecurityUtils.currentUserId();
        UserRole currentUserRole = SecurityUtils.currentUserRole();
        if (currentUserRole != UserRole.ADMIN && !currentUserId.equals(targetUserId)) {
            throw new AccessDeniedException("عدم دسترسی");
        }
    }
}
