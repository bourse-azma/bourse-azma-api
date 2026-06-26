package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.dto.UserResponse;
import com.ernoxin.bourseazmaapi.dto.WalletAdjustmentRequest;
import com.ernoxin.bourseazmaapi.dto.WalletTransactionResponse;
import com.ernoxin.bourseazmaapi.dto.api.PagedResponse;
import com.ernoxin.bourseazmaapi.exception.ResourceNotFoundException;
import com.ernoxin.bourseazmaapi.mapper.UserMapper;
import com.ernoxin.bourseazmaapi.mapper.WalletMapper;
import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserMapper userMapper;
    private final WalletMapper walletMapper;

    private String formatAmount(BigDecimal amount) {
        DecimalFormat formatter = new DecimalFormat("#,###");
        return formatter.format(amount.abs());
    }

    @Override
    @Transactional
    public UserResponse adjustBalance(Long userId, WalletAdjustmentRequest request) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("کاربر مورد نظر یافت نشد."));

        BigDecimal oldBalance = user.getBalance() != null ? user.getBalance() : BigDecimal.ZERO;
        BigDecimal newBalance;
        BigDecimal amount;
        String autoDescription;

        String type = request.getType().toUpperCase();
        BigDecimal value = request.getValue();

        switch (type) {
            case "SET":
                newBalance = value;
                amount = newBalance.subtract(oldBalance);
                autoDescription = String.format("تغییر موجودی از %s به %s ریال", formatAmount(oldBalance), formatAmount(newBalance));
                break;
            case "ADD":
                amount = value;
                newBalance = oldBalance.add(amount);
                autoDescription = String.format("افزایش موجودی به میزان %s ریال", formatAmount(amount));
                break;
            case "SUBTRACT":
                amount = value.negate();
                newBalance = oldBalance.add(amount);
                autoDescription = String.format("کاهش موجودی به میزان %s ریال", formatAmount(value));
                break;
            case "PERCENT_ADD":
                BigDecimal addAmount = oldBalance.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                amount = addAmount;
                newBalance = oldBalance.add(amount);
                autoDescription = String.format("افزایش موجودی به میزان %s درصد (مبلغ %s ریال)", value.toString(), formatAmount(addAmount));
                break;
            case "PERCENT_SUBTRACT":
                BigDecimal subAmount = oldBalance.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                amount = subAmount.negate();
                newBalance = oldBalance.add(amount);
                autoDescription = String.format("کاهش موجودی به میزان %s درصد (مبلغ %s ریال)", value.toString(), formatAmount(subAmount));
                break;
            default:
                throw new IllegalArgumentException("نوع عملیات نامعتبر است: " + type);
        }

        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("موجودی کیف پول نمی‌تواند منفی شود.");
        }

        user.setBalance(newBalance);
        userRepository.save(user);

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setBalanceAfter(newBalance);
        tx.setDescription(request.getDescription() != null && !request.getDescription().isBlank() ? request.getDescription() : autoDescription);
        tx.setCreatedAt(Instant.now());
        walletTransactionRepository.save(tx);

        return userMapper.toDto(user);
    }

    @Override
    public PagedResponse<WalletTransactionResponse> getTransactions(Long userId, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("کاربر مورد نظر یافت نشد.");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<WalletTransaction> result =
                walletTransactionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return new PagedResponse<>(
                walletMapper.toDtoList(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
