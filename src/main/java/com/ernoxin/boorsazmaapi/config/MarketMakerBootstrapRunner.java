package com.ernoxin.boorsazmaapi.config;

import com.ernoxin.boorsazmaapi.model.User;
import com.ernoxin.boorsazmaapi.model.UserRole;
import com.ernoxin.boorsazmaapi.repository.UserRepository;
import com.ernoxin.boorsazmaapi.service.MarketMakerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class MarketMakerBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(MarketMakerService.MARKET_MAKER_USERNAME)) {
            return;
        }

        User marketMaker = new User();
        marketMaker.setUsername(MarketMakerService.MARKET_MAKER_USERNAME);
        marketMaker.setFirstName("Market");
        marketMaker.setLastName("Maker");
        marketMaker.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        marketMaker.setRole(UserRole.USER);
        marketMaker.setBalance(BigDecimal.ZERO);
        userRepository.save(marketMaker);

        log.info("Market maker bootstrap user created: {}", MarketMakerService.MARKET_MAKER_USERNAME);
    }
}
