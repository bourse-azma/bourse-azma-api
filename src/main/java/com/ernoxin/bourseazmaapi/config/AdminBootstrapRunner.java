package com.ernoxin.bourseazmaapi.config;

import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserRole;
import com.ernoxin.bourseazmaapi.model.WalletTransaction;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import com.ernoxin.bourseazmaapi.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

@Component
@Order(10)
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:}")
    private String username;

    @Value("${app.bootstrap.admin.email:}")
    private String email;

    @Value("${app.bootstrap.admin.phone-number:}")
    private String phoneNumber;

    @Value("${app.bootstrap.admin.first-name:}")
    private String firstName;

    @Value("${app.bootstrap.admin.last-name:}")
    private String lastName;

    @Value("${app.bootstrap.admin.password:}")
    private String password;

    @Value("${app.bootstrap.admin.balance:0}")
    private BigDecimal balance;

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            return;
        }
        if (userRepository.existsByRole(UserRole.ADMIN)) {
            log.info("Admin bootstrap skipped: an admin user already exists.");
            return;
        }
        if (isBlank(username) || isBlank(firstName) || isBlank(lastName)) {
            log.warn("Admin bootstrap skipped: required properties are missing (username, first-name, last-name).");
            return;
        }
        if (password.length() < 8 || password.length() > 24) {
            log.warn("Admin bootstrap skipped: password must be between 8 and 24 characters.");
            return;
        }
        if (!username.matches("^[A-Za-z0-9._-]{3,50}$")) {
            log.warn("Admin bootstrap skipped: invalid username format.");
            return;
        }
        if (!isBlank(email) && !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            log.warn("Admin bootstrap skipped: invalid email format.");
            return;
        }
        if (!isBlank(phoneNumber) && !phoneNumber.matches("^09\\d{9}$")) {
            log.warn("Admin bootstrap skipped: invalid phone number format.");
            return;
        }
        if (userRepository.existsByUsername(username.toLowerCase(Locale.ROOT))) {
            log.warn("Admin bootstrap skipped: username already exists.");
            return;
        }
        if (!isBlank(email) && userRepository.existsByEmail(email.toLowerCase(Locale.ROOT))) {
            log.warn("Admin bootstrap skipped: email already exists.");
            return;
        }

        User admin = new User();
        admin.setUsername(username.toLowerCase(Locale.ROOT));
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setPhoneNumber(toNullIfBlank(phoneNumber));
        admin.setEmail(toNullIfBlank(email) == null ? null : email.toLowerCase(Locale.ROOT));
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        BigDecimal initialBalance = balance != null ? balance : BigDecimal.ZERO;
        admin.setBalance(initialBalance);
        User savedAdmin = userRepository.save(admin);

        WalletTransaction initialTx = new WalletTransaction();
        initialTx.setUser(savedAdmin);
        initialTx.setAmount(initialBalance);
        initialTx.setBalanceAfter(initialBalance);
        initialTx.setDescription("موجودی اولیه به مبلغ " + initialBalance.toPlainString() + " ریال هنگام ایجاد حساب مدیر");
        initialTx.setCreatedAt(Instant.now());
        walletTransactionRepository.save(initialTx);

        log.info("Admin bootstrap user created: {} (balance: {})", username, initialBalance);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String toNullIfBlank(String value) {
        if (isBlank(value)) return null;
        return value.trim();
    }
}
