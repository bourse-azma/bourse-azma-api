package com.ernoxin.bourseazmaapi.service.supportrequest;

import com.ernoxin.bourseazmaapi.dto.SupportRequestResponse;
import com.ernoxin.bourseazmaapi.model.SupportRequest;
import com.ernoxin.bourseazmaapi.model.SupportRequestClosedBy;
import com.ernoxin.bourseazmaapi.model.SupportRequestStatus;
import com.ernoxin.bourseazmaapi.repository.SupportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SupportRequestStateHandler {

    private final SupportRequestRepository supportRequestRepository;
    private final SupportRequestTextNormalizer textNormalizer;
    private final SupportRequestStatsLoader statsLoader;
    private final SupportRequestDtoMapper dtoMapper;

    public SupportRequestResponse closeRequest(
            SupportRequest supportRequest,
            boolean includeUser,
            SupportRequestClosedBy closedBy
    ) {
        if (textNormalizer.isClosedStatus(supportRequest.getStatus())) {
            throw new IllegalArgumentException("این تیکت قبلا بسته شده است.");
        }

        applyClosedStatus(supportRequest, closedBy);

        SupportRequest saved = supportRequestRepository.save(supportRequest);
        Map<Long, SupportRequestStatsLoader.MessageStats> statsById = statsLoader.loadMessageStats(List.of(saved.getId()));
        return dtoMapper.toSummaryDto(saved, includeUser, statsLoader.statsFor(saved, statsById), false);
    }

    public void applyClosedStatus(SupportRequest supportRequest, SupportRequestClosedBy closedBy) {
        Instant now = Instant.now();
        supportRequest.setStatus(SupportRequestStatus.CLOSED);
        supportRequest.setUpdatedAt(now);
        supportRequest.setClosedAt(now);
        supportRequest.setClosedBy(closedBy);
    }

    public void clearClosedMetadata(SupportRequest supportRequest) {
        supportRequest.setClosedAt(null);
        supportRequest.setClosedBy(null);
    }
}
