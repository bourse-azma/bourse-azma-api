package com.ernoxin.bourseazmaapi.repository;

import com.ernoxin.bourseazmaapi.model.UserActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {
    Page<UserActivityLog> findAllByUserIdAndActivityTypeInOrderByOccurredAtDesc(
            Long userId, Collection<String> activityTypes, Pageable pageable);

    long deleteByActivityTypeNotIn(Collection<String> activityTypes);
}
