package com.ernoxin.bourseazmaapi.repository;

import java.time.Instant;

public interface SupportRequestMessageStats {
    Long getSupportRequestId();

    Long getReplyCount();

    Instant getLastReplyAt();
}
