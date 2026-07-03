package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.User;
import com.ernoxin.bourseazmaapi.model.UserActivityLog;
import com.ernoxin.bourseazmaapi.repository.UserActivityLogRepository;
import com.ernoxin.bourseazmaapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;
    private final UserActivityLogRepository activityLogRepository;

    @Transactional
    public void touch(Long userId) {
        userRepository.updateLastSeenAt(userId, Instant.now());
    }

    @Transactional
    public void record(Long userId, String type) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;
        UserActivityLog log = new UserActivityLog();
        log.setUser(user);
        log.setActivityType(type);
        log.setOccurredAt(Instant.now());
        activityLogRepository.save(log);
    }
}
