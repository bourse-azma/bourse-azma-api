package com.ernoxin.bourseazmaapi.service;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestClosedBy;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import com.ernoxin.bourseazmaapi.repository.SupportRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SupportRequestAutoCloseScheduler {

    private final SupportRequestRepository supportRequestRepository;

    @Value("${app.support.auto-close-after-days:10}")
    private int autoCloseAfterDays;

    @Scheduled(fixedDelayString = "${app.support.auto-close-poll-interval-ms:3600000}")
    @Transactional
    public void autoCloseStaleTickets() {
        Instant cutoff = Instant.now().minus(autoCloseAfterDays, ChronoUnit.DAYS);
        List<SupportRequestStatus> openStatuses = List.of(
                SupportRequestStatus.OPEN,
                SupportRequestStatus.IN_PROGRESS
        );
        List<SupportRequest> staleTickets =
                supportRequestRepository.findAllByStatusInAndUpdatedAtBefore(openStatuses, cutoff);
        if (staleTickets.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (SupportRequest ticket : staleTickets) {
            ticket.setStatus(SupportRequestStatus.CLOSED);
            ticket.setUpdatedAt(now);
            ticket.setClosedAt(now);
            ticket.setClosedBy(SupportRequestClosedBy.SYSTEM);
            supportRequestRepository.save(ticket);
        }
        log.info("Auto-closed {} stale support ticket(s) older than {} day(s).",
                staleTickets.size(), autoCloseAfterDays);
    }
}
