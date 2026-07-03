package com.ernoxin.bourseazmaapi.config;

import com.ernoxin.bourseazmaapi.repository.UserActivityLogRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UserMetadataBootstrapRunner implements ApplicationRunner {
    private final UserRepository userRepository;
    private final UserActivityLogRepository activityLogRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.backfillMissingCreatedAt(Instant.now());
        activityLogRepository.deleteByActivityTypeNotIn(java.util.List.of("LOGIN", "LOGOUT"));
    }
}
