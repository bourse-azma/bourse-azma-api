package com.ernoxin.boorsazmaapi.service;

import com.ernoxin.boorsazmaapi.dto.UserCreateRequest;
import com.ernoxin.boorsazmaapi.dto.UserResponse;
import com.ernoxin.boorsazmaapi.dto.UserUpdateRequest;
import com.ernoxin.boorsazmaapi.dto.auth.RegisterRequest;
import com.ernoxin.boorsazmaapi.exception.DuplicateResourceException;
import com.ernoxin.boorsazmaapi.exception.InvalidCurrentPasswordException;
import com.ernoxin.boorsazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.boorsazmaapi.mapper.UserMapper;
import com.ernoxin.boorsazmaapi.model.User;
import com.ernoxin.boorsazmaapi.model.UserRole;
import com.ernoxin.boorsazmaapi.repository.UserRepository;
import com.ernoxin.boorsazmaapi.repository.WalletTransactionRepository;
import com.ernoxin.boorsazmaapi.model.WalletTransaction;
import com.ernoxin.boorsazmaapi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final BigDecimal DEFAULT_REGISTRATION_BALANCE = BigDecimal.valueOf(100_000_000L);

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        UserCreateRequest createRequest = new UserCreateRequest();
        createRequest.setUsername(request.getUsername());
        createRequest.setFirstName(request.getFirstName());
        createRequest.setLastName(request.getLastName());
        createRequest.setNationalCode(request.getNationalCode());
        createRequest.setPhoneNumber(request.getPhoneNumber());
        createRequest.setEmail(request.getEmail());
        createRequest.setPassword(request.getPassword());
        createRequest.setBalance(DEFAULT_REGISTRATION_BALANCE);
        return create(createRequest);
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        normalizeRequestForPersistence(request);
        validateUniqueFieldsForCreate(request);
        User user = userMapper.toEntity(request);
        user.setRole(UserRole.USER);

        BigDecimal initialBalance = request.getBalance() != null ? request.getBalance() : BigDecimal.valueOf(100_000_000L);
        user.setBalance(initialBalance);

        user.setPassword(passwordEncoder.encode(request.getPassword()));
        User savedUser = userRepository.save(user);

        // Save initial wallet transaction
        WalletTransaction initialTx = new WalletTransaction();
        initialTx.setUser(savedUser);
        initialTx.setAmount(initialBalance);
        initialTx.setBalanceAfter(initialBalance);
        initialTx.setDescription("موجودی اولیه به مبلغ " + initialBalance.toPlainString() + " ریال هنگام ثبت‌نام");
        initialTx.setCreatedAt(Instant.now());
        walletTransactionRepository.save(initialTx);

        return userMapper.toDto(savedUser);
    }

    @Override
    public UserResponse getById(Long id) {
        validateOwnerOrAdmin(id);
        return userMapper.toDto(findById(id));
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
        validateUniqueFieldsForUpdate(request);
        userMapper.updateEntity(request, user);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            validatePasswordChange(user, request);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
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
    public void delete(Long id) {
        validateOwnerOrAdmin(id);
        User user = findById(id);
        userRepository.delete(user);
    }

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر مورد نظر یافت نشد."));
    }

    private void validateUniqueFieldsForCreate(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("نام کاربری واردشده قبلا ثبت شده است.");
        }
        if (request.getNationalCode() != null && userRepository.existsByNationalCode(request.getNationalCode())) {
            throw new DuplicateResourceException("کد ملی واردشده قبلا ثبت شده است.");
        }
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new DuplicateResourceException("شماره موبایل واردشده قبلا ثبت شده است.");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("ایمیل واردشده قبلا ثبت شده است.");
        }
    }

    private void validateUniqueFieldsForUpdate(UserUpdateRequest request) {
        if (userRepository.existsByUsernameAndIdNot(request.getUsername(), request.getId())) {
            throw new DuplicateResourceException("نام کاربری واردشده قبلا ثبت شده است.");
        }
        if (request.getNationalCode() != null
                && userRepository.existsByNationalCodeAndIdNot(request.getNationalCode(), request.getId())) {
            throw new DuplicateResourceException("کد ملی واردشده قبلا ثبت شده است.");
        }
        if (request.getPhoneNumber() != null
                && userRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), request.getId())) {
            throw new DuplicateResourceException("شماره موبایل واردشده قبلا ثبت شده است.");
        }
        if (request.getEmail() != null && userRepository.existsByEmailAndIdNot(request.getEmail(), request.getId())) {
            throw new DuplicateResourceException("ایمیل واردشده قبلا ثبت شده است.");
        }
    }

    private void normalizeRequestForPersistence(UserCreateRequest request) {
        request.setUsername(normalizeUsername(request.getUsername()));
        request.setFirstName(normalizeRequired(request.getFirstName()));
        request.setLastName(normalizeRequired(request.getLastName()));
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setNationalCode(normalizeOptional(request.getNationalCode()));
        request.setPhoneNumber(normalizeOptional(request.getPhoneNumber()));
    }

    private void normalizeRequestForPersistence(UserUpdateRequest request) {
        request.setUsername(normalizeUsername(request.getUsername()));
        request.setFirstName(normalizeRequired(request.getFirstName()));
        request.setLastName(normalizeRequired(request.getLastName()));
        request.setEmail(normalizeEmail(request.getEmail()));
        request.setNationalCode(normalizeOptional(request.getNationalCode()));
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
