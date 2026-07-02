package com.ernoxin.bourseazmaapi.service.supportrequest;

import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageRepository;
import com.ernoxin.bourseazmaapi.repository.SupportRequestMessageStats;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SupportRequestStatsLoader {

    private final SupportRequestMessageRepository supportRequestMessageRepository;

    public Map<Long, MessageStats> loadMessageStats(List<Long> supportRequestIds) {
        if (supportRequestIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, MessageStats> statsById = new HashMap<>();
        for (SupportRequestMessageStats stats : supportRequestMessageRepository.aggregateStatsBySupportRequestIds(supportRequestIds)) {
            statsById.put(
                    stats.getSupportRequestId(),
                    new MessageStats(stats.getReplyCount(), stats.getLastReplyAt())
            );
        }
        return statsById;
    }

    public MessageStats statsFor(SupportRequest request, Map<Long, MessageStats> statsById) {
        MessageStats stats = statsById.get(request.getId());
        if (stats != null) {
            return stats;
        }
        return MessageStats.empty(request.getCreatedAt());
    }

    public record MessageStats(long replyCount, Instant lastReplyAt) {
        public static MessageStats empty(Instant createdAt) {
            return new MessageStats(0, createdAt);
        }
    }
}
